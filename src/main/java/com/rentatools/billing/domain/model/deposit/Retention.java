package com.rentatools.billing.domain.model.deposit;

import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object que representa la retención aplicada sobre el depósito de garantía
 * para cubrir total o parcialmente una factura de liquidación ({@code SETTLEMENT}).
 * En BD: {@code deposit_retentions(invoice_id, amount, applied_at)}.
 */
public record Retention(
        InvoiceId invoiceId,
        Money amount,
        Instant appliedAt
) {

    public Retention {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(appliedAt, "appliedAt must not be null");

        if (!amount.isPositive()) {
            throw new BusinessRuleViolation(
                    "INVALID_RETENTION_AMOUNT",
                    "Retention amount must be greater than zero, got: " + amount
            );
        }
    }
}
