package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload de entrada para registrar un pago sobre una factura.
 */
public record RegisterPaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "method is required")
        PaymentMethod method,

        @Size(max = 100, message = "reference must not exceed 100 characters")
        String reference
) {
}
