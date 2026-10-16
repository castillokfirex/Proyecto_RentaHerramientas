package com.rentatools.billing.domain.service;

import com.rentatools.billing.domain.model.invoice.InvoiceLine;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Política de dominio para el cálculo de recargos por devolución con retraso (mora):
 * <ul>
 *     <li>{@code unitPrice = dailyRate × lateFeeMultiplier} (con redondeo HALF_UP a 2 decimales)</li>
 *     <li>{@code quantity = lateDays}</li>
 *     <li>{@code recargo por mora = lateDays × dailyRate × lateFeeMultiplier}</li>
 * </ul>
 */
public final class LateFeePolicy {

    public LateFeePolicy() {
    }

    /**
     * Calcula el precio unitario por día de mora ({@code dailyRate × lateFeeMultiplier}).
     */
    public Money calculateDailyLateFeeRate(Money dailyRate, BigDecimal lateFeeMultiplier) {
        Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        Objects.requireNonNull(lateFeeMultiplier, "lateFeeMultiplier must not be null");
        if (!dailyRate.isPositive()) {
            throw new BusinessRuleViolation(
                    "INVALID_DAILY_RATE",
                    "Daily rate must be greater than zero, got: " + dailyRate
            );
        }
        if (lateFeeMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_LATE_FEE_MULTIPLIER",
                    "Late fee multiplier must be greater than zero, got: " + lateFeeMultiplier
            );
        }
        return dailyRate.times(lateFeeMultiplier);
    }

    /**
     * Calcula el recargo total por mora: {@code lateDays × dailyRate × lateFeeMultiplier}.
     * Si {@code lateDays == 0}, devuelve {@link Money#ZERO}.
     */
    public Money calculateLateFee(int lateDays, Money dailyRate, BigDecimal lateFeeMultiplier) {
        if (lateDays < 0) {
            throw new BusinessRuleViolation(
                    "INVALID_LATE_DAYS",
                    "Late days must not be negative, got: " + lateDays
            );
        }
        Money dailyLateFee = calculateDailyLateFeeRate(dailyRate, lateFeeMultiplier);
        if (lateDays == 0) {
            return Money.ZERO;
        }
        return dailyLateFee.times(lateDays);
    }

    /**
     * Crea una línea {@link LineConcept#LATE_FEE} para una unidad devuelta con {@code lateDays > 0}.
     */
    public InvoiceLine createLateFeeLine(
            UUID reservationUnitId,
            String description,
            int lateDays,
            Money dailyRate,
            BigDecimal lateFeeMultiplier
    ) {
        if (lateDays <= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_LATE_DAYS",
                    "Late fee line requires lateDays > 0, got: " + lateDays
            );
        }
        Money unitPrice = calculateDailyLateFeeRate(dailyRate, lateFeeMultiplier);
        return InvoiceLine.create(
                reservationUnitId,
                LineConcept.LATE_FEE,
                description,
                BigDecimal.valueOf(lateDays),
                unitPrice
        );
    }

    /**
     * Construye opcionalmente la línea de mora solo si {@code lateDays > 0}.
     */
    public Optional<InvoiceLine> buildLateFeeLineIfLate(
            UUID reservationUnitId,
            String toolName,
            int lateDays,
            Money dailyRate,
            BigDecimal lateFeeMultiplier
    ) {
        if (lateDays < 0) {
            throw new BusinessRuleViolation(
                    "INVALID_LATE_DAYS",
                    "Late days must not be negative, got: " + lateDays
            );
        }
        if (lateDays == 0) {
            return Optional.empty();
        }
        String label = (toolName == null || toolName.isBlank()) ? "Unidad" : toolName.trim();
        String description = "Recargo por mora (" + lateDays + " días) - " + label;
        return Optional.of(createLateFeeLine(reservationUnitId, description, lateDays, dailyRate, lateFeeMultiplier));
    }
}
