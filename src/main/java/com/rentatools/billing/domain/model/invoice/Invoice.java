package com.rentatools.billing.domain.model.invoice;

import com.rentatools.billing.domain.event.RentalInvoiceIssued;
import com.rentatools.billing.domain.exception.DuplicateChargeException;
import com.rentatools.billing.domain.exception.InvoiceNotDraftException;
import com.rentatools.billing.domain.exception.OverpaymentException;
import com.rentatools.billing.domain.service.InvoiceBalanceCalculator;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raíz de agregado que representa una factura ({@code RENTAL} o {@code SETTLEMENT}) asociada a una reserva.
 *
 * <p>Respeta estrictamente las formas normales 3FN/FNBC/4FN de {@code DDL-4FN.md}:</p>
 * <ul>
 *     <li>El estado ({@link InvoiceStatus}) no se persiste; se deriva de {@code issuedAt} y {@code voidedAt}.</li>
 *     <li>Los totales e importes pendientes no se persisten; se calculan a partir de las líneas, pagos, tasa de IVA y retención.</li>
 *     <li>Solo un borrador ({@link InvoiceStatus#DRAFT}) acepta agregar líneas o ser emitido.</li>
 *     <li>Cada par {@code (reservationUnitId, concept)} puede aparecer una única vez.</li>
 *     <li>Las líneas {@code RENTAL} van solo en facturas {@code RENTAL}; {@code LATE_FEE} y {@code DAMAGE} solo en {@code SETTLEMENT}.</li>
 *     <li>Nunca se permite sobrepago: {@code netPaid + retainedDeposit <= total}.</li>
 * </ul>
 */
public final class Invoice extends AggregateRoot {

    private static final InvoiceBalanceCalculator BALANCE_CALCULATOR = new InvoiceBalanceCalculator();

    private final InvoiceId id;
    private final UUID reservationId;
    private final InvoiceType type;
    private InvoiceNumber number;
    private final BigDecimal taxRate;
    private Instant issuedAt;
    private Instant voidedAt;
    private final Instant createdAt;
    private final List<InvoiceLine> lines;
    private final List<Payment> payments;

    private Invoice(
            InvoiceId id,
            UUID reservationId,
            InvoiceType type,
            InvoiceNumber number,
            BigDecimal taxRate,
            Instant issuedAt,
            Instant voidedAt,
            Instant createdAt,
            List<InvoiceLine> lines,
            List<Payment> payments,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.taxRate = validateAndNormalizeTaxRate(taxRate);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");

        if ((number == null) != (issuedAt == null)) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_ISSUE_STATE",
                    "Invoice number and issuedAt must either both be null (draft) or both be non-null (issued)"
            );
        }
        if (voidedAt != null && (issuedAt == null || voidedAt.isBefore(issuedAt))) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_VOID_STATE",
                    "A voided invoice must have been issued beforehand and voidedAt must be >= issuedAt"
            );
        }

        this.number = number;
        this.issuedAt = issuedAt;
        this.voidedAt = voidedAt;
        this.lines = new ArrayList<>();
        this.payments = new ArrayList<>();

        if (lines != null) {
            for (InvoiceLine line : lines) {
                validateAndAppendLine(line);
            }
        }
        if (payments != null) {
            for (Payment payment : payments) {
                this.payments.add(Objects.requireNonNull(payment, "payment must not be null"));
            }
        }
    }

    /**
     * Crea una factura en estado {@link InvoiceStatus#DRAFT} (sin número ni {@code issuedAt}).
     */
    public static Invoice createDraft(
            UUID reservationId,
            InvoiceType type,
            BigDecimal taxRate,
            Instant createdAt
    ) {
        return new Invoice(
                InvoiceId.newId(),
                reservationId,
                type,
                null,
                taxRate,
                null,
                null,
                createdAt,
                List.of(),
                List.of(),
                0L
        );
    }

    /**
     * Crea y emite directamente una factura {@link InvoiceType#RENTAL} con sus líneas de alquiler,
     * registrando el evento de dominio {@link RentalInvoiceIssued}.
     */
    public static Invoice openRentalInvoice(
            UUID reservationId,
            BigDecimal taxRate,
            List<InvoiceLine> initialLines,
            InvoiceNumber number,
            Instant now
    ) {
        Objects.requireNonNull(initialLines, "initialLines must not be null");
        Invoice invoice = createDraft(reservationId, InvoiceType.RENTAL, taxRate, now);
        for (InvoiceLine line : initialLines) {
            invoice.addLine(line);
        }
        invoice.issue(number, now);
        return invoice;
    }

    /**
     * Reconstruye una factura desde la capa de persistencia sin emitir eventos de dominio.
     */
    public static Invoice reconstitute(
            InvoiceId id,
            UUID reservationId,
            InvoiceType type,
            InvoiceNumber number,
            BigDecimal taxRate,
            Instant issuedAt,
            Instant voidedAt,
            Instant createdAt,
            List<InvoiceLine> lines,
            List<Payment> payments,
            long version
    ) {
        return new Invoice(
                id,
                reservationId,
                type,
                number,
                taxRate,
                issuedAt,
                voidedAt,
                createdAt,
                lines,
                payments,
                version
        );
    }

    /**
     * Estado derivado según {@code issuedAt} y {@code voidedAt}.
     */
    public InvoiceStatus status() {
        if (voidedAt != null) {
            return InvoiceStatus.VOIDED;
        }
        if (issuedAt != null) {
            return InvoiceStatus.ISSUED;
        }
        return InvoiceStatus.DRAFT;
    }

    public boolean isDraft() {
        return status() == InvoiceStatus.DRAFT;
    }

    public boolean isIssued() {
        return status() == InvoiceStatus.ISSUED;
    }

    public boolean isVoided() {
        return status() == InvoiceStatus.VOIDED;
    }

    /**
     * Agrega una línea a la factura en borrador verificando que no exista otra línea
     * para el mismo {@code (reservationUnitId, concept)} y que el concepto corresponda al tipo de factura.
     */
    public InvoiceLine addLine(
            UUID reservationUnitId,
            LineConcept concept,
            String description,
            BigDecimal quantity,
            Money unitPrice
    ) {
        InvoiceLine line = InvoiceLine.create(reservationUnitId, concept, description, quantity, unitPrice);
        return addLine(line);
    }

    public InvoiceLine addLine(
            UUID reservationUnitId,
            LineConcept concept,
            String description,
            long quantity,
            Money unitPrice
    ) {
        return addLine(reservationUnitId, concept, description, BigDecimal.valueOf(quantity), unitPrice);
    }

    public InvoiceLine addLine(InvoiceLine line) {
        assertDraft("Cannot add lines to an invoice with status " + status());
        validateAndAppendLine(line);
        return line;
    }

    /**
     * Emite una factura en borrador asignándole su número secuencial y marca de tiempo {@code issuedAt}.
     * Si la factura es de tipo {@link InvoiceType#RENTAL}, registra {@link RentalInvoiceIssued}.
     */
    public void issue(InvoiceNumber assignedNumber, Instant issuedTimestamp) {
        assertDraft("Only a draft invoice can be issued; current status: " + status());
        Objects.requireNonNull(assignedNumber, "assignedNumber must not be null");
        Objects.requireNonNull(issuedTimestamp, "issuedTimestamp must not be null");
        if (lines.isEmpty()) {
            throw new BusinessRuleViolation("INVOICE_EMPTY", "Cannot issue an invoice without lines");
        }
        if (issuedTimestamp.isBefore(createdAt)) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_ISSUE_DATE",
                    "issuedAt must not be before createdAt"
            );
        }
        this.number = assignedNumber;
        this.issuedAt = issuedTimestamp;

        if (this.type == InvoiceType.RENTAL) {
            registerEvent(RentalInvoiceIssued.of(this.id, this.reservationId, this.number, total(), this.issuedAt));
        }
    }

    /**
     * Anula una factura emitida registrando {@code voidedAt}.
     */
    public void voidInvoice(Instant voidedTimestamp) {
        Objects.requireNonNull(voidedTimestamp, "voidedTimestamp must not be null");
        if (isDraft()) {
            throw new BusinessRuleViolation("CANNOT_VOID_DRAFT_INVOICE", "Cannot void a draft invoice");
        }
        if (isVoided()) {
            throw new BusinessRuleViolation("INVOICE_ALREADY_VOIDED", "Invoice is already voided");
        }
        if (voidedTimestamp.isBefore(this.issuedAt)) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_VOID_DATE",
                    "voidedAt must not be before issuedAt"
            );
        }
        this.voidedAt = voidedTimestamp;
    }

    /**
     * Registra un pago del cliente sobre la factura (sin retención de garantía).
     */
    public Payment registerPayment(
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        return registerPayment(amount, method, reference, occurredAt, recordedBy, Money.ZERO);
    }

    /**
     * Registra un pago del cliente teniendo en cuenta la retención de garantía ya aplicada (si aplica).
     * Lanza {@link OverpaymentException} si el importe supera el saldo pendiente.
     */
    public Payment registerPayment(
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy,
            Money retainedDeposit
    ) {
        if (!isIssued()) {
            throw new BusinessRuleViolation(
                    "INVOICE_NOT_PAYABLE",
                    "Payments can only be registered on ISSUED invoices; current status: " + status()
            );
        }
        Payment payment = Payment.create(PaymentKind.PAYMENT, amount, method, reference, occurredAt, recordedBy);
        Money pendingBalance = balance(retainedDeposit);
        if (payment.amount().compareTo(pendingBalance) > 0) {
            throw new OverpaymentException(
                    "Payment amount " + payment.amount().amount()
                            + " exceeds invoice remaining balance " + pendingBalance.amount()
            );
        }
        this.payments.add(payment);
        return payment;
    }

    /**
     * Registra una devolución ({@link PaymentKind#REFUND}) sobre lo efectivamente pagado en la factura.
     * No permite devolver más del neto pagado ({@code netPaid}).
     */
    public Payment registerRefund(
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        if (isDraft()) {
            throw new BusinessRuleViolation(
                    "INVOICE_REFUND_NOT_ALLOWED",
                    "Cannot register a refund on a draft invoice"
            );
        }
        Payment refund = Payment.create(PaymentKind.REFUND, amount, method, reference, occurredAt, recordedBy);
        Money currentNetPaid = netPaid();
        if (refund.amount().compareTo(currentNetPaid) > 0) {
            throw new BusinessRuleViolation(
                    "REFUND_EXCEEDS_PAID",
                    "Refund amount " + refund.amount().amount()
                            + " exceeds net paid amount " + currentNetPaid.amount()
            );
        }
        this.payments.add(refund);
        return refund;
    }

    /**
     * Valida que una retención de garantía por {@code retentionAmount} sea aplicable a esta factura
     * (solo facturas {@code SETTLEMENT} emitidas y sin superar el saldo pendiente).
     */
    public void validateRetention(Money retentionAmount) {
        Objects.requireNonNull(retentionAmount, "retentionAmount must not be null");
        if (this.type != InvoiceType.SETTLEMENT) {
            throw new BusinessRuleViolation(
                    "RETENTION_ONLY_FOR_SETTLEMENT",
                    "Deposit retention can only be applied to SETTLEMENT invoices"
            );
        }
        if (!isIssued()) {
            throw new BusinessRuleViolation(
                    "RETENTION_REQUIRES_ISSUED_INVOICE",
                    "Deposit retention requires an ISSUED settlement invoice"
            );
        }
        if (retentionAmount.compareTo(balance(Money.ZERO)) > 0) {
            throw new OverpaymentException(
                    "Retention amount " + retentionAmount.amount()
                            + " exceeds settlement invoice balance " + balance(Money.ZERO).amount()
            );
        }
    }

    public InvoiceTotals calculateTotals() {
        return BALANCE_CALCULATOR.calculate(this, Money.ZERO);
    }

    public InvoiceTotals calculateTotals(Money retainedDeposit) {
        return BALANCE_CALCULATOR.calculate(this, retainedDeposit);
    }

    public Money subtotal() {
        return calculateTotals().subtotal();
    }

    public Money tax() {
        return calculateTotals().tax();
    }

    public Money total() {
        return calculateTotals().total();
    }

    public Money netPaid() {
        return BALANCE_CALCULATOR.calculateNetPaid(this.payments);
    }

    public Money balance() {
        return calculateTotals(Money.ZERO).balance();
    }

    public Money balance(Money retainedDeposit) {
        return calculateTotals(retainedDeposit).balance();
    }

    public InvoiceId id() {
        return id;
    }

    public UUID reservationId() {
        return reservationId;
    }

    public InvoiceType type() {
        return type;
    }

    public InvoiceNumber number() {
        return number;
    }

    public BigDecimal taxRate() {
        return taxRate;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant voidedAt() {
        return voidedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<InvoiceLine> lines() {
        return List.copyOf(lines);
    }

    public List<Payment> payments() {
        return List.copyOf(payments);
    }

    private void assertDraft(String message) {
        if (!isDraft()) {
            throw new InvoiceNotDraftException(message);
        }
    }

    private void validateAndAppendLine(InvoiceLine line) {
        Objects.requireNonNull(line, "line must not be null");
        if (!line.concept().isAllowedFor(this.type)) {
            throw new BusinessRuleViolation(
                    "INVALID_LINE_CONCEPT_FOR_INVOICE_TYPE",
                    "Concept " + line.concept() + " is not allowed in invoice of type " + this.type
            );
        }
        boolean duplicateExists = this.lines.stream().anyMatch(existing ->
                existing.reservationUnitId().equals(line.reservationUnitId())
                        && existing.concept() == line.concept()
        );
        if (duplicateExists) {
            throw new DuplicateChargeException(
                    "Duplicate line for reservationUnitId=" + line.reservationUnitId()
                            + " and concept=" + line.concept()
            );
        }
        this.lines.add(line);
    }

    private static BigDecimal validateAndNormalizeTaxRate(BigDecimal taxRate) {
        Objects.requireNonNull(taxRate, "taxRate must not be null");
        BigDecimal normalized = taxRate.setScale(4, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_TAX_RATE",
                    "Tax rate must satisfy 0 <= taxRate < 1, got: " + taxRate
            );
        }
        return normalized;
    }
}
