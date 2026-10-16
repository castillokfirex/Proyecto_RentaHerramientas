package com.rentatools.billing.domain.service;

import com.rentatools.billing.domain.event.SettlementCompleted;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Política de dominio que calcula y ejecuta la liquidación final ({@code SETTLEMENT}) de una reserva
 * contra su depósito de garantía ({@link SecurityDeposit}):
 * <ul>
 *     <li>{@code retainedFromDeposit = min(total SETTLEMENT, saldo disponible de garantía)}</li>
 *     <li>{@code refundToClient = saldo disponible de garantía − retainedFromDeposit}</li>
 *     <li>{@code remainingInvoiceBalance = total SETTLEMENT − retainedFromDeposit}</li>
 * </ul>
 */
public final class SettlementCalculator {

    public SettlementCalculator() {
    }

    /**
     * Resultado inmutable del cálculo de liquidación entre la factura SETTLEMENT y la garantía.
     */
    public record SettlementResult(
            Money settlementTotal,
            Money retainedFromDeposit,
            Money refundToClient,
            Money remainingInvoiceBalance
    ) {
        public SettlementResult {
            Objects.requireNonNull(settlementTotal, "settlementTotal must not be null");
            Objects.requireNonNull(retainedFromDeposit, "retainedFromDeposit must not be null");
            Objects.requireNonNull(refundToClient, "refundToClient must not be null");
            Objects.requireNonNull(remainingInvoiceBalance, "remainingInvoiceBalance must not be null");
        }

        public boolean isFullyCoveredByDeposit() {
            return remainingInvoiceBalance.isZero();
        }
    }

    /**
     * Calcula la distribución de la liquidación a partir del total de la factura SETTLEMENT
     * y el saldo disponible de la garantía.
     */
    public SettlementResult calculate(Money settlementTotal, Money availableDepositBalance) {
        Objects.requireNonNull(settlementTotal, "settlementTotal must not be null");
        Objects.requireNonNull(availableDepositBalance, "availableDepositBalance must not be null");
        if (settlementTotal.isNegative()) {
            throw new BusinessRuleViolation(
                    "INVALID_SETTLEMENT_TOTAL",
                    "Settlement total must not be negative, got: " + settlementTotal
            );
        }
        if (availableDepositBalance.isNegative()) {
            throw new BusinessRuleViolation(
                    "INVALID_AVAILABLE_DEPOSIT",
                    "Available deposit balance must not be negative, got: " + availableDepositBalance
            );
        }

        Money retained = settlementTotal.min(availableDepositBalance);
        Money refundToClient = availableDepositBalance.minus(retained);
        Money remainingInvoiceBalance = settlementTotal.minus(retained);

        return new SettlementResult(settlementTotal, retained, refundToClient, remainingInvoiceBalance);
    }

    /**
     * Calcula la liquidación para una factura {@code SETTLEMENT} (o {@code null} / sin líneas)
     * y una garantía {@link SecurityDeposit}.
     */
    public SettlementResult calculate(Invoice settlementInvoice, SecurityDeposit deposit) {
        Objects.requireNonNull(deposit, "deposit must not be null");
        if (settlementInvoice == null || settlementInvoice.lines().isEmpty()) {
            return calculate(Money.ZERO, deposit.balance());
        }
        if (settlementInvoice.type() != InvoiceType.SETTLEMENT) {
            throw new BusinessRuleViolation(
                    "INVALID_SETTLEMENT_INVOICE_TYPE",
                    "Expected SETTLEMENT invoice, got: " + settlementInvoice.type()
            );
        }
        return calculate(settlementInvoice.total(), deposit.balance());
    }

    /**
     * Ejecuta la liquidación sobre los agregados {@link Invoice} (opcional si no hay cargos)
     * y {@link SecurityDeposit}:
     * <ol>
     *     <li>Si la factura SETTLEMENT tiene líneas y está en borrador, la emite con {@code numberToAssign}.</li>
     *     <li>Aplica la retención {@code min(total, saldo garantía)} sobre la garantía.</li>
     *     <li>Registra el {@code REFUND} del excedente de garantía al cliente (si {@code refundToClient > 0}).</li>
     *     <li>Marca la garantía como liquidada ({@code settledAt = now}) y registra {@link SettlementCompleted}.</li>
     * </ol>
     */
    public SettlementResult settle(
            Invoice settlementInvoice,
            InvoiceNumber numberToAssignIfDraft,
            SecurityDeposit deposit,
            PaymentMethod refundMethod,
            String refundReference,
            Instant now,
            UUID recordedBy
    ) {
        Objects.requireNonNull(deposit, "deposit must not be null");
        Objects.requireNonNull(refundMethod, "refundMethod must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(recordedBy, "recordedBy must not be null");

        if (settlementInvoice != null) {
            if (settlementInvoice.type() != InvoiceType.SETTLEMENT) {
                throw new BusinessRuleViolation(
                        "INVALID_SETTLEMENT_INVOICE_TYPE",
                        "Expected SETTLEMENT invoice, got: " + settlementInvoice.type()
                );
            }
            if (!settlementInvoice.reservationId().equals(deposit.reservationId())) {
                throw new BusinessRuleViolation(
                        "SETTLEMENT_RESERVATION_MISMATCH",
                        "Settlement invoice and security deposit belong to different reservations"
                );
            }
        }

        boolean hasCharges = settlementInvoice != null && !settlementInvoice.lines().isEmpty();
        if (!hasCharges) {
            SettlementResult result = calculate(Money.ZERO, deposit.balance());
            deposit.refundRemainingAndSettle(refundMethod, refundReference, now, recordedBy);
            deposit.recordSettlementCompleted(
                    null,
                    result.retainedFromDeposit(),
                    result.refundToClient(),
                    result.settlementTotal(),
                    result.remainingInvoiceBalance(),
                    now
            );
            return result;
        }

        if (settlementInvoice.isDraft()) {
            Objects.requireNonNull(
                    numberToAssignIfDraft,
                    "numberToAssignIfDraft is required when issuing a draft settlement invoice"
            );
            settlementInvoice.issue(numberToAssignIfDraft, now);
        }

        SettlementResult result = calculate(settlementInvoice.total(), deposit.balance());
        InvoiceId invoiceId = settlementInvoice.id();

        if (result.retainedFromDeposit().isPositive()) {
            settlementInvoice.validateRetention(result.retainedFromDeposit());
            deposit.retain(invoiceId, result.retainedFromDeposit(), now);
        }
        if (result.refundToClient().isPositive()) {
            deposit.refund(result.refundToClient(), refundMethod, refundReference, now, recordedBy);
        }
        deposit.settle(now);
        deposit.recordSettlementCompleted(
                invoiceId,
                result.retainedFromDeposit(),
                result.refundToClient(),
                result.settlementTotal(),
                result.remainingInvoiceBalance(),
                now
        );

        return result;
    }
}
