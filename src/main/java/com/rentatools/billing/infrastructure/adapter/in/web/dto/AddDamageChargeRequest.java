package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload de entrada para agregar un cargo por daño ({@code DAMAGE}) a una factura {@code SETTLEMENT} en borrador.
 */
public record AddDamageChargeRequest(
        @NotNull(message = "reservationUnitId is required")
        UUID reservationUnitId,

        @NotBlank(message = "description is required")
        @Size(max = 200, message = "description must not exceed 200 characters")
        String description,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount
) {
}
