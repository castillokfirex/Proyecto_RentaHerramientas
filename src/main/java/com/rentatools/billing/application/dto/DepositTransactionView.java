package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.deposit.DepositTransaction;
import com.rentatools.billing.domain.model.deposit.DepositTransactionKind;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO de lectura para un movimiento (cobro o devolución) de garantía.
 */
public record DepositTransactionView(
        UUID id,
        DepositTransactionKind kind,
        BigDecimal amount,
        PaymentMethod method,
        String reference,
        Instant occurredAt,
        UUID recordedBy
) {

    public static DepositTransactionView from(DepositTransaction tx) {
        Objects.requireNonNull(tx, "tx must not be null");
        return new DepositTransactionView(
                tx.id(),
                tx.kind(),
                tx.amount().amount(),
                tx.method(),
                tx.reference(),
                tx.occurredAt(),
                tx.recordedBy()
        );
    }
}
