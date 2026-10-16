package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.invoice.Payment;
import com.rentatools.billing.domain.model.invoice.PaymentKind;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO de lectura para un pago o reembolso registrado en una factura.
 */
public record PaymentView(
        UUID id,
        PaymentKind kind,
        BigDecimal amount,
        PaymentMethod method,
        String reference,
        Instant occurredAt,
        UUID recordedBy
) {

    public static PaymentView from(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");
        return new PaymentView(
                payment.id(),
                payment.kind(),
                payment.amount().amount(),
                payment.method(),
                payment.reference(),
                payment.occurredAt(),
                payment.recordedBy()
        );
    }
}
