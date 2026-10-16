package com.rentatools.billing.domain.model.invoice;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad interna del agregado {@link Invoice} que representa un pago o devolución registrado sobre la factura.
 * En BD: {@code payments} (tabla de eventos, importes siempre mayores que cero).
 */
public record Payment(
        UUID id,
        PaymentKind kind,
        Money amount,
        PaymentMethod method,
        String reference,
        Instant occurredAt,
        UUID recordedBy
) {

    public Payment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(recordedBy, "recordedBy must not be null");

        if (!amount.isPositive()) {
            throw new BusinessRuleViolation(
                    "INVALID_PAYMENT_AMOUNT",
                    "Payment amount must be greater than zero, got: " + amount
            );
        }

        if (reference != null) {
            String trimmed = reference.trim();
            if (trimmed.length() > 100) {
                throw new BusinessRuleViolation(
                        "INVALID_PAYMENT_REFERENCE",
                        "Payment reference must not exceed 100 characters"
                );
            }
            reference = trimmed.isEmpty() ? null : trimmed;
        }
    }

    public static Payment create(
            PaymentKind kind,
            Money amount,
            PaymentMethod method,
            String reference,
            Instant occurredAt,
            UUID recordedBy
    ) {
        return new Payment(Ids.newId(), kind, amount, method, reference, occurredAt, recordedBy);
    }
}
