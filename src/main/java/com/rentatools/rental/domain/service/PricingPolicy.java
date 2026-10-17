package com.rentatools.rental.domain.service;

import com.rentatools.rental.domain.model.pricing.QuoteLine;
import com.rentatools.rental.domain.model.pricing.RateCard;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de dominio para calcular el precio pactado por unidad según la política de reparto voraz:
 * <ol>
 *   <li>Paquetes de 30 días si existe tarifa {@code MONTH}.</li>
 *   <li>Paquetes de 7 días sobre el remanente si existe tarifa {@code WEEK}.</li>
 *   <li>Días restantes a tarifa {@code DAY}.</li>
 *   <li>El precio final acordado por unidad es {@code min(repartoVoraz, N * DAY)}.</li>
 * </ol>
 */
public class PricingPolicy {

    private static final long DAYS_PER_MONTH = 30L;
    private static final long DAYS_PER_WEEK = 7L;

    public PricingPolicy() {
    }

    /**
     * Calcula el importe acordado por una unidad para {@code days} días aplicando el reparto voraz
     * acotado por {@code days * dailyRate}.
     */
    public Money calculateAgreedAmount(RateCard rateCard, long days) {
        Objects.requireNonNull(rateCard, "rateCard must not be null");
        if (days <= 0) {
            throw new BusinessRuleViolation("INVALID_RENTAL_DAYS", "Rental days must be >= 1");
        }

        long remainingDays = days;
        Money greedyTotal = Money.of("0.00", rateCard.dailyRate().currency().getCurrencyCode());

        if (rateCard.monthlyRate() != null) {
            long months = remainingDays / DAYS_PER_MONTH;
            remainingDays = remainingDays % DAYS_PER_MONTH;
            if (months > 0) {
                greedyTotal = greedyTotal.plus(rateCard.monthlyRate().times(months));
            }
        }

        if (rateCard.weeklyRate() != null) {
            long weeks = remainingDays / DAYS_PER_WEEK;
            remainingDays = remainingDays % DAYS_PER_WEEK;
            if (weeks > 0) {
                greedyTotal = greedyTotal.plus(rateCard.weeklyRate().times(weeks));
            }
        }

        if (remainingDays > 0) {
            greedyTotal = greedyTotal.plus(rateCard.dailyRate().times(remainingDays));
        }

        Money dailyOnlyTotal = rateCard.dailyRate().times(days);
        return greedyTotal.min(dailyOnlyTotal);
    }

    /**
     * Calcula el importe acordado por una unidad para el {@link DateRange} inclusivo dado.
     */
    public Money calculateAgreedAmount(RateCard rateCard, DateRange dateRange) {
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        return calculateAgreedAmount(rateCard, dateRange.days());
    }

    /**
     * Construye una línea de cotización ({@link QuoteLine}) para una herramienta, cantidad y rango de fechas.
     */
    public QuoteLine quoteLine(UUID toolId, int quantity, RateCard rateCard, DateRange dateRange) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(rateCard, "rateCard must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        Money unitAgreedAmount = calculateAgreedAmount(rateCard, dateRange.days());
        return QuoteLine.of(
                toolId,
                quantity,
                rateCard.dailyRate(),
                unitAgreedAmount,
                rateCard.depositAmount()
        );
    }
}
