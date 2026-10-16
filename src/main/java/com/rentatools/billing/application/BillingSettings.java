package com.rentatools.billing.application;

import com.rentatools.shared.domain.BusinessRuleViolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Configuración de negocio del contexto acotado {@code billing} (Java 21 puro).
 *
 * @param taxRate           Tasa de impuesto vigente al crear facturas ({@code 0 <= taxRate < 1}).
 * @param lateFeeMultiplier Multiplicador sobre la tarifa diaria para calcular la mora ({@code > 0}).
 */
public record BillingSettings(
        BigDecimal taxRate,
        BigDecimal lateFeeMultiplier
) {

    public BillingSettings {
        Objects.requireNonNull(taxRate, "taxRate must not be null");
        Objects.requireNonNull(lateFeeMultiplier, "lateFeeMultiplier must not be null");

        taxRate = taxRate.setScale(4, RoundingMode.HALF_UP);
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_TAX_RATE",
                    "taxRate must satisfy 0 <= taxRate < 1, got: " + taxRate
            );
        }

        lateFeeMultiplier = lateFeeMultiplier.setScale(2, RoundingMode.HALF_UP);
        if (lateFeeMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_LATE_FEE_MULTIPLIER",
                    "lateFeeMultiplier must be greater than zero, got: " + lateFeeMultiplier
            );
        }
    }
}
