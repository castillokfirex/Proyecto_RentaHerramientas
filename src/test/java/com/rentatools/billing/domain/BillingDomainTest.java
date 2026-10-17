package com.rentatools.billing.domain;

import com.rentatools.billing.domain.event.DepositCollected;
import com.rentatools.billing.domain.event.RentalInvoiceIssued;
import com.rentatools.billing.domain.event.SettlementCompleted;
import com.rentatools.billing.domain.exception.DepositAlreadySettledException;
import com.rentatools.billing.domain.exception.DuplicateChargeException;
import com.rentatools.billing.domain.exception.InvoiceNotDraftException;
import com.rentatools.billing.domain.exception.OverpaymentException;
import com.rentatools.billing.domain.model.deposit.DepositTransactionKind;
import com.rentatools.billing.domain.model.deposit.Retention;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.deposit.SettlementResult;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceLine;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.InvoiceTotals;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.billing.domain.model.invoice.PaymentKind;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.billing.domain.service.InvoiceBalanceCalculator;
import com.rentatools.billing.domain.service.LateFeePolicy;
import com.rentatools.billing.domain.service.SettlementCalculator;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingDomainTest {

    private static final BigDecimal TAX_RATE_19 = new BigDecimal("0.19");
    private static final BigDecimal LATE_FEE_MULTIPLIER = new BigDecimal("1.50");
    private static final Instant T0 = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-05-01T11:00:00Z");
    private static final Instant T2 = Instant.parse("2026-05-01T12:00:00Z");

    @Nested
    @DisplayName("Invoice - Estado derivado e invariantes")
    class InvoiceInvariantsTest {

        @Test
        @DisplayName("Deriva el estado DRAFT -> ISSUED -> VOIDED a partir de issuedAt y voidedAt")
        void derivesStatusFromTimestamps() {
            UUID reservationId = Ids.newId();
            UUID unitId = Ids.newId();

            Invoice invoice = Invoice.createDraft(reservationId, InvoiceType.RENTAL, TAX_RATE_19, T0);
            assertThat(invoice.status()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(invoice.isDraft()).isTrue();
            assertThat(invoice.number()).isNull();
            assertThat(invoice.issuedAt()).isNull();

            invoice.addLine(unitId, LineConcept.RENTAL, "Alquiler taladro percutor", 1L, Money.of("100000"));
            invoice.issue(InvoiceNumber.of(1001L), T1);

            assertThat(invoice.status()).isEqualTo(InvoiceStatus.ISSUED);
            assertThat(invoice.isIssued()).isTrue();
            assertThat(invoice.number()).isEqualTo(InvoiceNumber.of(1001L));
            assertThat(invoice.issuedAt()).isEqualTo(T1);

            List<DomainEvent> events = invoice.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(RentalInvoiceIssued.class);
            RentalInvoiceIssued issuedEvent = (RentalInvoiceIssued) events.get(0);
            assertThat(issuedEvent.invoiceId()).isEqualTo(invoice.id());
            assertThat(issuedEvent.reservationId()).isEqualTo(reservationId);
            assertThat(issuedEvent.number()).isEqualTo(InvoiceNumber.of(1001L));
            assertThat(issuedEvent.total()).isEqualTo(Money.of("119000.00"));

            invoice.voidInvoice(T2);
            assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOIDED);
            assertThat(invoice.isVoided()).isTrue();
            assertThat(invoice.voidedAt()).isEqualTo(T2);
        }

        @Test
        @DisplayName("Solo un borrador acepta agregar líneas o ser emitido (InvoiceNotDraftException)")
        void onlyDraftAcceptsNewLinesOrIssue() {
            UUID reservationId = Ids.newId();
            UUID unit1 = Ids.newId();
            UUID unit2 = Ids.newId();

            Invoice invoice = Invoice.openRentalInvoice(
                    reservationId,
                    TAX_RATE_19,
                    List.of(InvoiceLine.create(unit1, LineConcept.RENTAL, "Unidad 1", 1L, Money.of("50000"))),
                    InvoiceNumber.of(1L),
                    T0
            );

            assertThatThrownBy(() -> invoice.addLine(unit2, LineConcept.RENTAL, "Unidad 2", 1L, Money.of("50000")))
                    .isInstanceOf(InvoiceNotDraftException.class)
                    .extracting("code")
                    .isEqualTo(InvoiceNotDraftException.CODE);

            assertThatThrownBy(() -> invoice.issue(InvoiceNumber.of(2L), T1))
                    .isInstanceOf(InvoiceNotDraftException.class);

            invoice.voidInvoice(T1);

            assertThatThrownBy(() -> invoice.addLine(unit2, LineConcept.RENTAL, "Unidad 2", 1L, Money.of("50000")))
                    .isInstanceOf(InvoiceNotDraftException.class);
        }

        @Test
        @DisplayName("Rechaza líneas duplicadas para el mismo par (reservation_unit_id, concept)")
        void rejectsDuplicateUnitAndConcept() {
            UUID reservationId = Ids.newId();
            UUID unit1 = Ids.newId();

            Invoice settlement = Invoice.createDraft(reservationId, InvoiceType.SETTLEMENT, TAX_RATE_19, T0);
            settlement.addLine(unit1, LineConcept.DAMAGE, "Carcasa rota", 1L, Money.of("80000"));
            // Misma unidad pero distinto concepto (LATE_FEE) es válido en SETTLEMENT
            settlement.addLine(unit1, LineConcept.LATE_FEE, "2 días de mora", 2L, Money.of("30000"));

            // Repetir (unit1, DAMAGE) debe lanzar DuplicateChargeException
            assertThatThrownBy(() -> settlement.addLine(unit1, LineConcept.DAMAGE, "Otro daño", 1L, Money.of("20000")))
                    .isInstanceOf(DuplicateChargeException.class)
                    .extracting("code")
                    .isEqualTo(DuplicateChargeException.CODE);
        }

        @Test
        @DisplayName("Valida que el concepto de la línea corresponda al tipo de factura (DDL-4FN §8 regla 5)")
        void enforcesLineConceptMatchesInvoiceType() {
            UUID reservationId = Ids.newId();
            UUID unitId = Ids.newId();

            Invoice rentalInvoice = Invoice.createDraft(reservationId, InvoiceType.RENTAL, TAX_RATE_19, T0);
            assertThatThrownBy(() -> rentalInvoice.addLine(unitId, LineConcept.DAMAGE, "Daño", 1L, Money.of("10000")))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("INVALID_LINE_CONCEPT_FOR_INVOICE_TYPE");

            Invoice settlementInvoice = Invoice.createDraft(reservationId, InvoiceType.SETTLEMENT, TAX_RATE_19, T0);
            assertThatThrownBy(() -> settlementInvoice.addLine(unitId, LineConcept.RENTAL, "Alquiler", 1L, Money.of("10000")))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("INVALID_LINE_CONCEPT_FOR_INVOICE_TYPE");
        }

        @Test
        @DisplayName("No permite emitir una factura sin líneas ni con número <= 0")
        void rejectsIssuingEmptyInvoiceOrInvalidInvoiceNumber() {
            Invoice draft = Invoice.createDraft(Ids.newId(), InvoiceType.SETTLEMENT, TAX_RATE_19, T0);

            assertThatThrownBy(() -> draft.issue(InvoiceNumber.of(10L), T1))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("INVOICE_EMPTY");

            assertThatThrownBy(() -> InvoiceNumber.of(0L))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("INVALID_INVOICE_NUMBER");
        }
    }

    @Nested
    @DisplayName("InvoiceBalanceCalculator & Pagos - Totales, IVA HALF_UP, sin sobrepago y REFUND")
    class InvoiceTotalsAndPaymentsTest {

        private final InvoiceBalanceCalculator calculator = new InvoiceBalanceCalculator();

        @Test
        @DisplayName("Calcula subtotal, IVA con redondeo HALF_UP a 2 decimales y total")
        void calculatesSubtotalTaxHalfUpAndTotal() {
            UUID reservationId = Ids.newId();
            Invoice draft = Invoice.createDraft(reservationId, InvoiceType.SETTLEMENT, TAX_RATE_19, T0);
            // 1 * 33.33 + 2 * 10.01 = 33.33 + 20.02 = 53.35
            // IVA 19% de 53.35 = 10.1365 -> HALF_UP a 2 decimales = 10.14
            // Total = 53.35 + 10.14 = 63.49
            draft.addLine(Ids.newId(), LineConcept.DAMAGE, "Daño menor", new BigDecimal("1.00"), Money.of("33.33"));
            draft.addLine(Ids.newId(), LineConcept.LATE_FEE, "Mora", new BigDecimal("2.00"), Money.of("10.01"));

            InvoiceTotals totals = calculator.calculate(draft);
            assertThat(totals.subtotal()).isEqualTo(Money.of("53.35"));
            assertThat(totals.tax()).isEqualTo(Money.of("10.14"));
            assertThat(totals.total()).isEqualTo(Money.of("63.49"));
            assertThat(totals.netPaid()).isEqualTo(Money.ZERO);
            assertThat(totals.retainedDeposit()).isEqualTo(Money.ZERO);
            assertThat(totals.balance()).isEqualTo(Money.of("63.49"));
            assertThat(totals.isPaid()).isFalse();

            // Caso borde exacto .005 -> redondea hacia arriba (.01) con HALF_UP
            // subtotal = 10.50, taxRate = 0.19 -> 10.50 * 0.19 = 1.995 -> 2.00
            Money taxHalfUp = calculator.calculateTax(Money.of("10.50"), TAX_RATE_19);
            assertThat(taxHalfUp).isEqualTo(Money.of("2.00"));
        }

        @Test
        @DisplayName("Impide sobrepagos y permite pagos parciales, pago total y devoluciones (REFUND)")
        void preventsOverpaymentAndSupportsRefunds() {
            UUID reservationId = Ids.newId();
            UUID actorId = Ids.newId();
            Invoice invoice = Invoice.openRentalInvoice(
                    reservationId,
                    TAX_RATE_19,
                    List.of(InvoiceLine.create(Ids.newId(), LineConcept.RENTAL, "Alquiler", 1L, Money.of("100000"))),
                    InvoiceNumber.of(50L),
                    T0
            );
            // Total = 100,000 + 19,000 = 119,000.00
            assertThat(invoice.total()).isEqualTo(Money.of("119000.00"));
            assertThat(invoice.balance()).isEqualTo(Money.of("119000.00"));

            // Pago parcial de 50,000
            invoice.registerPayment(Money.of("50000"), PaymentMethod.CARD, "TX-1", T1, actorId);
            assertThat(invoice.netPaid()).isEqualTo(Money.of("50000.00"));
            assertThat(invoice.balance()).isEqualTo(Money.of("69000.00"));

            // Intentar pagar 69,000.01 lanza OverpaymentException
            assertThatThrownBy(() -> invoice.registerPayment(
                    Money.of("69000.01"), PaymentMethod.CARD, "TX-OVER", T1, actorId
            ))
                    .isInstanceOf(OverpaymentException.class)
                    .extracting("code")
                    .isEqualTo(OverpaymentException.CODE);

            // Pagar el saldo exacto (69,000.00) deja el balance en 0
            invoice.registerPayment(Money.of("69000.00"), PaymentMethod.TRANSFER, "TX-2", T1, actorId);
            assertThat(invoice.balance()).isEqualTo(Money.ZERO);
            assertThat(invoice.calculateTotals().isPaid()).isTrue();

            // Cualquier pago adicional con saldo 0 lanza OverpaymentException
            assertThatThrownBy(() -> invoice.registerPayment(
                    Money.of("1.00"), PaymentMethod.CASH, "TX-3", T2, actorId
            )).isInstanceOf(OverpaymentException.class);

            // Anular y devolver (flujo de cancelación §3.4 paso 3)
            invoice.voidInvoice(T2);
            assertThatThrownBy(() -> invoice.registerRefund(
                    Money.of("120000.00"), PaymentMethod.TRANSFER, "REF-EXCESS", T2, actorId
            ))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("REFUND_EXCEEDS_PAID");

            var refund = invoice.registerRefund(Money.of("119000.00"), PaymentMethod.TRANSFER, "REF-FULL", T2, actorId);
            assertThat(refund.kind()).isEqualTo(PaymentKind.REFUND);
            assertThat(invoice.netPaid()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("LateFeePolicy - Cálculo de recargos por mora")
    class LateFeePolicyTest {

        private final LateFeePolicy lateFeePolicy = new LateFeePolicy();

        @Test
        @DisplayName("Calcula mora = lateDays × dailyRate × lateFeeMultiplier y genera línea LATE_FEE")
        void calculatesLateFeeAndBuildsInvoiceLine() {
            UUID unitId = Ids.newId();
            Money dailyRate = Money.of("40000.00");

            // 0 días de retraso -> sin mora
            assertThat(lateFeePolicy.calculateLateFee(0, dailyRate, LATE_FEE_MULTIPLIER)).isEqualTo(Money.ZERO);
            assertThat(lateFeePolicy.buildLateFeeLineIfLate(unitId, "Taladro", 0, dailyRate, LATE_FEE_MULTIPLIER))
                    .isEmpty();

            // 3 días × 40,000 × 1.50 = 3 × 60,000 = 180,000.00
            Money totalLateFee = lateFeePolicy.calculateLateFee(3, dailyRate, LATE_FEE_MULTIPLIER);
            assertThat(totalLateFee).isEqualTo(Money.of("180000.00"));

            Optional<InvoiceLine> lineOpt = lateFeePolicy.buildLateFeeLineIfLate(
                    unitId, "Rotomartillo Bosch", 3, dailyRate, LATE_FEE_MULTIPLIER
            );
            assertThat(lineOpt).isPresent();
            InvoiceLine line = lineOpt.get();
            assertThat(line.concept()).isEqualTo(LineConcept.LATE_FEE);
            assertThat(line.reservationUnitId()).isEqualTo(unitId);
            assertThat(line.quantity()).isEqualByComparingTo("3.00");
            assertThat(line.unitPrice()).isEqualTo(Money.of("60000.00"));
            assertThat(line.lineTotal()).isEqualTo(Money.of("180000.00"));
        }

        @Test
        @DisplayName("Rechaza días negativos o multiplicador no positivo")
        void rejectsInvalidInputs() {
            assertThatThrownBy(() -> lateFeePolicy.calculateLateFee(-1, Money.of("10000"), LATE_FEE_MULTIPLIER))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("INVALID_LATE_DAYS");

            assertThatThrownBy(() -> lateFeePolicy.calculateDailyLateFeeRate(Money.of("10000"), BigDecimal.ZERO))
                    .isInstanceOf(BusinessRuleViolation.class)
                    .extracting("code")
                    .isEqualTo("INVALID_LATE_FEE_MULTIPLIER");
        }
    }

    @Nested
    @DisplayName("SecurityDeposit & SettlementCalculator - Garantía, retenciones, devolución e inmutabilidad")
    class SecurityDepositAndSettlementTest {

        private final SettlementCalculator settlementCalculator = new SettlementCalculator();

        @Test
        @DisplayName("Calcula saldo derivado (COLLECTION - REFUND - retenciones) e impide cobrar más de lo requerido")
        void managesDepositCollectionsAndDerivedBalance() {
            UUID reservationId = Ids.newId();
            UUID actorId = Ids.newId();
            SecurityDeposit deposit = SecurityDeposit.open(reservationId, Money.of("200000"), T0);

            assertThat(deposit.balance()).isEqualTo(Money.ZERO);
            assertThat(deposit.isFullyCollected()).isFalse();
            assertThat(deposit.isSettled()).isFalse();

            // Cobro parcial de 120,000
            deposit.collect(Money.of("120000"), PaymentMethod.SIMULATED, "DEP-1", T1, actorId);
            assertThat(deposit.balance()).isEqualTo(Money.of("120000.00"));
            assertThat(deposit.pendingCollectionAmount()).isEqualTo(Money.of("80000.00"));
            assertThat(deposit.isFullyCollected()).isFalse();

            // Intentar cobrar 90,000 cuando faltan 80,000 lanza OverpaymentException
            assertThatThrownBy(() -> deposit.collect(Money.of("90000"), PaymentMethod.SIMULATED, "DEP-OVER", T1, actorId))
                    .isInstanceOf(OverpaymentException.class);

            // Cobrar los 80,000 restantes completa la garantía requerida
            deposit.collect(Money.of("80000"), PaymentMethod.SIMULATED, "DEP-2", T1, actorId);
            assertThat(deposit.balance()).isEqualTo(Money.of("200000.00"));
            assertThat(deposit.isFullyCollected()).isTrue();

            List<DomainEvent> events = deposit.pullDomainEvents();
            assertThat(events).hasSize(2).allMatch(DepositCollected.class::isInstance);
        }

        @Test
        @DisplayName("Liquidación cuando garantía cubre el total de SETTLEMENT: retiene total y devuelve el excedente")
        void settlesWhenDepositCoversFullSettlementTotal() {
            UUID reservationId = Ids.newId();
            UUID actorId = Ids.newId();
            UUID unitId = Ids.newId();

            // Garantía cobrada: 200,000.00
            SecurityDeposit deposit = SecurityDeposit.open(reservationId, Money.of("200000"), T0);
            deposit.collect(Money.of("200000"), PaymentMethod.SIMULATED, "DEP-FULL", T0, actorId);
            deposit.pullDomainEvents();

            // Factura SETTLEMENT en borrador: daño de 100,000 + IVA 19% (19,000) = 119,000.00
            Invoice settlement = Invoice.createDraft(reservationId, InvoiceType.SETTLEMENT, TAX_RATE_19, T1);
            settlement.addLine(unitId, LineConcept.DAMAGE, "Reparación carcasa", 1L, Money.of("100000"));

            SettlementResult result = settlementCalculator.settle(
                    settlement,
                    InvoiceNumber.of(2001L),
                    deposit,
                    PaymentMethod.SIMULATED,
                    "REF-SETTLE",
                    T2,
                    actorId
            );

            // Retención = min(119,000, 200,000) = 119,000; Devolución al cliente = 81,000; Saldo factura = 0
            assertThat(result.settlementTotal()).isEqualTo(Money.of("119000.00"));
            assertThat(result.retainedFromDeposit()).isEqualTo(Money.of("119000.00"));
            assertThat(result.refundToClient()).isEqualTo(Money.of("81000.00"));
            assertThat(result.remainingInvoiceBalance()).isEqualTo(Money.ZERO);
            assertThat(result.isFullyCoveredByDeposit()).isTrue();

            assertThat(settlement.isIssued()).isTrue();
            assertThat(settlement.number()).isEqualTo(InvoiceNumber.of(2001L));
            assertThat(settlement.balance(result.retainedFromDeposit())).isEqualTo(Money.ZERO);

            assertThat(deposit.isSettled()).isTrue();
            assertThat(deposit.settledAt()).isEqualTo(T2);
            assertThat(deposit.totalRetained()).isEqualTo(Money.of("119000.00"));
            assertThat(deposit.totalRefunded()).isEqualTo(Money.of("81000.00"));
            assertThat(deposit.balance()).isEqualTo(Money.ZERO);
            assertThat(deposit.retentions())
                    .containsExactly(new Retention(settlement.id(), Money.of("119000.00"), T2));

            List<DomainEvent> events = deposit.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(SettlementCompleted.class);
            SettlementCompleted completed = (SettlementCompleted) events.get(0);
            assertThat(completed.retainedFromDeposit()).isEqualTo(Money.of("119000.00"));
            assertThat(completed.refundedFromDeposit()).isEqualTo(Money.of("81000.00"));
            assertThat(completed.extraChargesTotal()).isEqualTo(Money.of("119000.00"));
            assertThat(completed.remainingBalance()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("Liquidación cuando SETTLEMENT supera la garantía: retiene toda la garantía y queda saldo por pagar")
        void settlesWhenSettlementExceedsDepositBalance() {
            UUID reservationId = Ids.newId();
            UUID actorId = Ids.newId();
            UUID unitId = Ids.newId();

            // Garantía cobrada: 100,000.00
            SecurityDeposit deposit = SecurityDeposit.open(reservationId, Money.of("100000"), T0);
            deposit.collect(Money.of("100000"), PaymentMethod.CARD, "DEP-100", T0, actorId);
            deposit.pullDomainEvents();

            // Factura SETTLEMENT: 200,000 + IVA 19% (38,000) = 238,000.00
            Invoice settlement = Invoice.createDraft(reservationId, InvoiceType.SETTLEMENT, TAX_RATE_19, T1);
            settlement.addLine(unitId, LineConcept.DAMAGE, "Pérdida de accesorio y daño", 1L, Money.of("200000"));

            SettlementResult result = settlementCalculator.settle(
                    settlement,
                    InvoiceNumber.of(2002L),
                    deposit,
                    PaymentMethod.CARD,
                    "REF-NONE",
                    T2,
                    actorId
            );

            // Retención = min(238,000, 100,000) = 100,000; Devolución = 0; Saldo pendiente en factura = 138,000
            assertThat(result.settlementTotal()).isEqualTo(Money.of("238000.00"));
            assertThat(result.retainedFromDeposit()).isEqualTo(Money.of("100000.00"));
            assertThat(result.refundToClient()).isEqualTo(Money.ZERO);
            assertThat(result.remainingInvoiceBalance()).isEqualTo(Money.of("138000.00"));
            assertThat(result.isFullyCoveredByDeposit()).isFalse();

            assertThat(deposit.isSettled()).isTrue();
            assertThat(deposit.balance()).isEqualTo(Money.ZERO);
            assertThat(deposit.totalRefunded()).isEqualTo(Money.ZERO);
            assertThat(deposit.totalRetained()).isEqualTo(Money.of("100000.00"));

            // El cliente ahora paga el saldo restante de la factura SETTLEMENT (teniendo en cuenta la retención)
            assertThat(settlement.balance(result.retainedFromDeposit())).isEqualTo(Money.of("138000.00"));
            assertThatThrownBy(() -> settlement.registerPayment(
                    Money.of("138000.01"),
                    PaymentMethod.CARD,
                    "PAY-EXCESS",
                    T2,
                    actorId,
                    result.retainedFromDeposit()
            )).isInstanceOf(OverpaymentException.class);

            settlement.registerPayment(
                    Money.of("138000.00"),
                    PaymentMethod.CARD,
                    "PAY-REM",
                    T2,
                    actorId,
                    result.retainedFromDeposit()
            );
            assertThat(settlement.balance(result.retainedFromDeposit())).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("Liquidación sin cargos adicionales devuelve el 100% de la garantía y la deja inmutable")
        void settlesWithFullRefundWhenNoExtraChargesAndEnforcesImmutability() {
            UUID reservationId = Ids.newId();
            UUID actorId = Ids.newId();

            SecurityDeposit deposit = SecurityDeposit.open(reservationId, Money.of("150000"), T0);
            deposit.collect(Money.of("150000"), PaymentMethod.TRANSFER, "DEP-150", T0, actorId);
            deposit.pullDomainEvents();

            Invoice emptySettlementDraft = Invoice.createDraft(reservationId, InvoiceType.SETTLEMENT, TAX_RATE_19, T1);

            SettlementResult result = settlementCalculator.settle(
                    emptySettlementDraft,
                    null,
                    deposit,
                    PaymentMethod.TRANSFER,
                    "REF-ALL",
                    T2,
                    actorId
            );

            assertThat(result.settlementTotal()).isEqualTo(Money.ZERO);
            assertThat(result.retainedFromDeposit()).isEqualTo(Money.ZERO);
            assertThat(result.refundToClient()).isEqualTo(Money.of("150000.00"));
            assertThat(result.remainingInvoiceBalance()).isEqualTo(Money.ZERO);
            assertThat(deposit.isSettled()).isTrue();
            assertThat(deposit.balance()).isEqualTo(Money.ZERO);
            assertThat(deposit.transactions()).hasSize(2);
            assertThat(deposit.transactions().get(1).kind()).isEqualTo(DepositTransactionKind.REFUND);
            assertThat(deposit.transactions().get(1).amount()).isEqualTo(Money.of("150000.00"));

            // Una vez liquidada (settledAt != null), es estrictamente inmutable
            assertThatThrownBy(() -> deposit.collect(Money.of("10000"), PaymentMethod.CASH, "X", T2, actorId))
                    .isInstanceOf(DepositAlreadySettledException.class)
                    .extracting("code")
                    .isEqualTo(DepositAlreadySettledException.CODE);

            assertThatThrownBy(() -> deposit.refund(Money.of("10000"), PaymentMethod.CASH, "X", T2, actorId))
                    .isInstanceOf(DepositAlreadySettledException.class);

            assertThatThrownBy(() -> deposit.retain(InvoiceId.newId(), Money.of("10000"), T2))
                    .isInstanceOf(DepositAlreadySettledException.class);

            assertThatThrownBy(() -> deposit.settle(T2))
                    .isInstanceOf(DepositAlreadySettledException.class);
        }
    }
}
