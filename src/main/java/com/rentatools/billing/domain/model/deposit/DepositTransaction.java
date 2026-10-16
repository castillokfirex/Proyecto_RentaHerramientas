package com.rentatools.billing.domain.model.deposit;

import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad interna del agregado {@link SecurityDeposit} que representa un cobro ({@code COLLECTION})
 * o una devolución ({@code REFUND}) de garantía.
 */
public record DepositTransaction(
        UUID id,
        DepositTransactionKind kind,
        Money amount,
        PaymentMethod method,
        String reference,
        Instant occurredAt,
        UUID recordedBy
) {

    public DepositTransaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(recordedBy, "recordedBy must not be null");

        if (!amount.isPositive()) {
            throw new BusinessRuleViolation(
                    "INVALID_DEPOSIT_TRANSACTION_AMOUNT",
                    "Deposit transaction amount must be greater than zero, got: " + amount
            );
        }

        if (reference != null) {
            String trimmed = reference.trim();
            if (trimmed.length() > 100) {
                throw new BusinessRuleViolation(
                        "INVALID_DEPOSIT_TRANSACTION_REFERENCE",
                        "Deposit transaction reference must not exceed 100 characters"
                );
            }
            reference = trimmed.isEmpty() ? null : trimmed;
        }
    }

    public static DepositTransaction create(
            DepositTransactionKind kind,
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        return new DepositTransaction(Ids.newId(), kind, amount, method, reference, occurredAt, recordedBy);
    }
}
