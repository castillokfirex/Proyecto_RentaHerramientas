package com.rentatools.inventory.domain.model.tool;

import com.rentatools.shared.domain.Money;
import java.util.Objects;

/**
 * Value Object que representa la tarifa de alquiler de una herramienta para un periodo ({@code DAY}, {@code WEEK}, {@code MONTH}).
 *
 * <p>Invariante: el precio debe ser estrictamente positivo ({@code price > 0}).
 */
public record ToolRate(RatePeriod period, Money price) {

    public ToolRate {
        Objects.requireNonNull(period, "RatePeriod must not be null");
        Objects.requireNonNull(price, "Rate price must not be null");
        if (!price.isPositive()) {
            throw new IllegalArgumentException("ToolRate price must be strictly positive (> 0), got: " + price);
        }
    }
}
