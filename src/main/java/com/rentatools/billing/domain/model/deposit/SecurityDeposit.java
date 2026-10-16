package com.rentatools.billing.domain.model.deposit;

import com.rentatools.billing.domain.event.DepositCollected;
import com.rentatools.billing.domain.exception.DepositAlreadySettledException;
import com.rentatools.billing.domain.exception.OverpaymentException;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Raíz de agregado que representa el depósito de garantía ({@code security_deposits}) de una reserva.
 *
 * <p>Reglas e invariantes clave ({@code PLAN.md} §3.1, §3.4 y {@code DDL-4FN.md}):</p>
 * <ul>
 *     <li>No existen columnas de estado ni de saldo en BD: el saldo disponible se deriva como
 *         {@code Σ COLLECTION − Σ REFUND − Σ retenciones}.</li>
 *     <li>El monto cobrado neto no puede superar {@code requiredAmount}.</li>
 *     <li>El saldo disponible nunca puede volverse negativo ({@code balance >= 0}).</li>
 *     <li>Una vez liquidada ({@code settledAt != null}), la garantía es estrictamente inmutable y cualquier
 *         intento de movimiento o retención lanza {@link DepositAlreadySettledException}.</li>
 * </ul>
 */
public final class SecurityDeposit extends AggregateRoot {

    private final DepositId id;
    private final UUID reservationId;
    private final Money requiredAmount;
    private final Instant createdAt;
    private Instant settledAt;
    private final List<DepositTransaction> transactions;
    private final List<Retention> retentions;

    private SecurityDeposit(
            DepositId id,
            UUID reservationId,
            Money requiredAmount,
            Instant createdAt,
            Instant settledAt,
            List<DepositTransaction> transactions,
            List<Retention> retentions,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.requiredAmount = Objects.requireNonNull(requiredAmount, "requiredAmount must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (requiredAmount.isNegative()) {
            throw new BusinessRuleViolation(
                    "INVALID_REQUIRED_DEPOSIT",
                    "Required deposit amount must not be negative, got: " + requiredAmount
            );
        }
        if (settledAt != null && settledAt.isBefore(createdAt)) {
            throw new BusinessRuleViolation(
                    "INVALID_DEPOSIT_SETTLED_DATE",
                    "settledAt must not be before createdAt"
            );
        }

        this.settledAt = settledAt;
        this.transactions = new ArrayList<>();
        this.retentions = new ArrayList<>();

        if (transactions != null) {
            for (DepositTransaction tx : transactions) {
                this.transactions.add(Objects.requireNonNull(tx, "transaction must not be null"));
            }
        }
        if (retentions != null) {
            for (Retention retention : retentions) {
                this.retentions.add(Objects.requireNonNull(retention, "retention must not be null"));
            }
        }
        if (balance().isNegative()) {
            throw new BusinessRuleViolation(
                    "NEGATIVE_DEPOSIT_BALANCE",
                    "Security deposit balance cannot be negative"
            );
        }
    }

    public static SecurityDeposit open(UUID reservationId, Money requiredAmount, Instant createdAt) {
        return new SecurityDeposit(
                DepositId.newId(),
                reservationId,
                requiredAmount,
                createdAt,
                null,
                List.of(),
                List.of(),
                0L
        );
    }

    public static SecurityDeposit reconstitute(
            DepositId id,
            UUID reservationId,
            Money requiredAmount,
            Instant createdAt,
            Instant settledAt,
            List<DepositTransaction> transactions,
            List<Retention> retentions,
            long version
    ) {
        return new SecurityDeposit(
                id,
                reservationId,
                requiredAmount,
                createdAt,
                settledAt,
                transactions,
                retentions,
                version
        );
    }

    /**
     * Registra un cobro ({@link DepositTransactionKind#COLLECTION}) de garantía sin permitir superar {@code requiredAmount}.
     */
    public DepositTransaction collect(
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        assertNotSettled();
        DepositTransaction transaction = DepositTransaction.create(
                DepositTransactionKind.COLLECTION,
                amount,
                method,
                reference,
                occurredAt,
                recordedBy
        );
        Money pendingToCollect = pendingCollectionAmount();
        if (transaction.amount().compareTo(pendingToCollect) > 0) {
            throw new OverpaymentException(
                    "Deposit collection " + transaction.amount().amount()
                            + " exceeds pending required deposit " + pendingToCollect.amount()
            );
        }
        this.transactions.add(transaction);
        registerEvent(DepositCollected.of(
                this.id,
                this.reservationId,
                transaction.amount(),
                balance(),
                this.requiredAmount,
                occurredAt
        ));
        return transaction;
    }

    /**
     * Aplica una retención sobre el saldo disponible de la garantía para cubrir una factura {@code SETTLEMENT}.
     */
    public Retention retain(InvoiceId settlementInvoiceId, Money amount, Instant appliedAt) {
        assertNotSettled();
        Retention retention = new Retention(settlementInvoiceId, amount, appliedAt);
        boolean alreadyRetainedForInvoice = this.retentions.stream()
                .anyMatch(existing -> existing.invoiceId().equals(settlementInvoiceId));
        if (alreadyRetainedForInvoice) {
            throw new BusinessRuleViolation(
                    "DUPLICATE_DEPOSIT_RETENTION",
                    "A retention has already been applied for invoice " + settlementInvoiceId.value()
            );
        }
        Money available = balance();
        if (retention.amount().compareTo(available) > 0) {
            throw new BusinessRuleViolation(
                    "INSUFFICIENT_DEPOSIT_BALANCE",
                    "Retention amount " + retention.amount().amount()
                            + " exceeds available deposit balance " + available.amount()
            );
        }
        this.retentions.add(retention);
        return retention;
    }

    /**
     * Registra una devolución ({@link DepositTransactionKind#REFUND}) al cliente por un monto hasta el saldo disponible.
     */
    public DepositTransaction refund(
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        assertNotSettled();
        DepositTransaction transaction = DepositTransaction.create(
                DepositTransactionKind.REFUND,
                amount,
                method,
                reference,
                occurredAt,
                recordedBy
        );
        Money available = balance();
        if (transaction.amount().compareTo(available) > 0) {
            throw new BusinessRuleViolation(
                    "REFUND_EXCEEDS_DEPOSIT_BALANCE",
                    "Refund amount " + transaction.amount().amount()
                            + " exceeds available deposit balance " + available.amount()
            );
        }
        this.transactions.add(transaction);
        return transaction;
    }

    /**
     * Marca la garantía como liquidada ({@code settledAt != null}).
     * Exige que el saldo disponible sea 0 (tras aplicar retenciones y/o devoluciones).
     */
    public void settle(Instant settledTimestamp) {
        assertNotSettled();
        Objects.requireNonNull(settledTimestamp, "settledTimestamp must not be null");
        if (settledTimestamp.isBefore(this.createdAt)) {
            throw new BusinessRuleViolation(
                    "INVALID_DEPOSIT_SETTLED_DATE",
                    "settledAt must not be before createdAt"
            );
        }
        if (!balance().isZero()) {
            throw new BusinessRuleViolation(
                    "DEPOSIT_BALANCE_NOT_ZERO",
                    "Cannot mark deposit as settled while available balance is " + balance().amount()
            );
        }
        this.settledAt = settledTimestamp;
    }

    /**
     * Devuelve todo el saldo disponible (si lo hubiera) y marca la garantía como liquidada.
     * Útil en cancelaciones de reserva o liquidaciones sin cargos.
     */
    public Optional<DepositTransaction> refundRemainingAndSettle(
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        assertNotSettled();
        DepositTransaction refundTx = null;
        Money available = balance();
        if (available.isPositive()) {
            refundTx = refund(available, method, reference, occurredAt, recordedBy);
        }
        settle(occurredAt);
        return Optional.ofNullable(refundTx);
    }

    /**
     * Registra el evento de dominio {@link com.rentatools.billing.domain.event.SettlementCompleted}
     * al concluir la liquidación de la garantía.
     */
    public void recordSettlementCompleted(
            InvoiceId settlementInvoiceId,
            Money retainedFromDeposit,
            Money refundedFromDeposit,
            Money extraChargesTotal,
            Money remainingBalance,
            Instant occurredAt
    ) {
        registerEvent(com.rentatools.billing.domain.event.SettlementCompleted.of(
                this.reservationId,
                settlementInvoiceId,
                retainedFromDeposit,
                refundedFromDeposit,
                extraChargesTotal,
                remainingBalance,
                occurredAt
        ));
    }

    public boolean isSettled() {
        return settledAt != null;
    }

    /**
     * Suma de todos los movimientos {@link DepositTransactionKind#COLLECTION}.
     */
    public Money totalCollected() {
        Money sum = Money.ZERO;
        for (DepositTransaction tx : transactions) {
            if (tx.kind() == DepositTransactionKind.COLLECTION) {
                sum = sum.plus(tx.amount());
            }
        }
        return sum;
    }

    /**
     * Suma de todos los movimientos {@link DepositTransactionKind#REFUND}.
     */
    public Money totalRefunded() {
        Money sum = Money.ZERO;
        for (DepositTransaction tx : transactions) {
            if (tx.kind() == DepositTransactionKind.REFUND) {
                sum = sum.plus(tx.amount());
            }
        }
        return sum;
    }

    /**
     * Suma de todas las retenciones aplicadas a facturas de liquidación.
     */
    public Money totalRetained() {
        Money sum = Money.ZERO;
        for (Retention retention : retentions) {
            sum = sum.plus(retention.amount());
        }
        return sum;
    }

    /**
     * Saldo disponible derivado: {@code Σ COLLECTION − Σ REFUND − Σ retenciones}.
     */
    public Money balance() {
        return totalCollected().minus(totalRefunded()).minus(totalRetained());
    }

    /**
     * Importe pendiente por cobrar respecto al total requerido.
     */
    public Money pendingCollectionAmount() {
        Money netCollected = totalCollected().minus(totalRefunded());
        if (netCollected.compareTo(requiredAmount) >= 0) {
            return Money.ZERO;
        }
        return requiredAmount.minus(netCollected);
    }

    /**
     * Indica si ya se ha cobrado la totalidad de la garantía requerida.
     */
    public boolean isFullyCollected() {
        return totalCollected().minus(totalRefunded()).compareTo(requiredAmount) >= 0;
    }

    public Optional<Retention> findRetentionForInvoice(InvoiceId invoiceId) {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        return retentions.stream()
                .filter(r -> r.invoiceId().equals(invoiceId))
                .findFirst();
    }

    public DepositId id() {
        return id;
    }

    public UUID reservationId() {
        return reservationId;
    }

    public Money requiredAmount() {
        return requiredAmount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant settledAt() {
        return settledAt;
    }

    public List<DepositTransaction> transactions() {
        return List.copyOf(transactions);
    }

    public List<Retention> retentions() {
        return List.copyOf(retentions);
    }

    private void assertNotSettled() {
        if (isSettled()) {
            throw new DepositAlreadySettledException(
                    "Security deposit " + id.value() + " is already settled and immutable"
            );
        }
    }
}
