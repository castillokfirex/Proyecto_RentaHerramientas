package com.rentatools.rental.domain.model.pricing;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;
import java.util.Objects;

/**
 * Value Object que agrupa las tarifas vigentes de una herramienta para el cálculo de alquiler:
 * tarifa diaria (obligatoria > 0), tarifa semanal (opcional > 0), tarifa mensual (opcional > 0)
 * y depósito de garantía por unidad (>= 0).
 */
public record RateCard(
        Money dailyRate,
        Money weeklyRate,
        Money monthlyRate,
        Money depositAmount
) {

    public RateCard {
        Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        if (!dailyRate.isPositive()) {
            throw new BusinessRuleViolation("INVALID_DAILY_RATE", "dailyRate must be > 0");
        }
        if (weeklyRate != null && !weeklyRate.isPositive()) {
            throw new BusinessRuleViolation("INVALID_WEEKLY_RATE", "weeklyRate must be > 0 when present");
        }
        if (monthlyRate != null && !monthlyRate.isPositive()) {
            throw new BusinessRuleViolation("INVALID_MONTHLY_RATE", "monthlyRate must be > 0 when present");
        }
        if (depositAmount == null) {
            depositAmount = Money.ZERO;
        } else if (depositAmount.isNegative()) {
            throw new BusinessRuleViolation("INVALID_DEPOSIT_AMOUNT", "depositAmount must be >= 0");
        }
    }

    public RateCard(Money dailyRate, Money weeklyRate, Money monthlyRate) {
        this(dailyRate, weeklyRate, monthlyRate, Money.ZERO);
    }

    public static RateCard of(Money dailyRate, Money weeklyRate, Money monthlyRate, Money depositAmount) {
        return new RateCard(dailyRate, weeklyRate, monthlyRate, depositAmount);
    }

    public static RateCard of(Money dailyRate, Money weeklyRate, Money monthlyRate) {
        return new RateCard(dailyRate, weeklyRate, monthlyRate, Money.ZERO);
    }

    public static RateCard dailyOnly(Money dailyRate) {
        return new RateCard(dailyRate, null, null, Money.ZERO);
    }

    public static RateCard dailyOnly(Money dailyRate, Money depositAmount) {
        return new RateCard(dailyRate, null, null, depositAmount);
    }
}
