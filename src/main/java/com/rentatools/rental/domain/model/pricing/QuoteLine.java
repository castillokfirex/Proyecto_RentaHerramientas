package com.rentatools.rental.domain.model.pricing;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Línea cotizada para una herramienta y una cantidad de unidades solicitadas.
 */
public record QuoteLine(
        UUID toolId,
        int quantity,
        Money dailyRate,
        Money unitAgreedAmount,
        Money unitDepositAmount,
        Money lineAgreedAmount,
        Money lineDepositAmount
) {

    public QuoteLine {
        Objects.requireNonNull(toolId, "toolId must not be null");
        if (quantity <= 0) {
            throw new BusinessRuleViolation("INVALID_QUOTE_QUANTITY", "quantity must be > 0");
        }
        Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        Objects.requireNonNull(unitAgreedAmount, "unitAgreedAmount must not be null");
        Objects.requireNonNull(unitDepositAmount, "unitDepositAmount must not be null");
        Objects.requireNonNull(lineAgreedAmount, "lineAgreedAmount must not be null");
        Objects.requireNonNull(lineDepositAmount, "lineDepositAmount must not be null");
    }

    public static QuoteLine of(
            UUID toolId,
            int quantity,
            Money dailyRate,
            Money unitAgreedAmount,
            Money unitDepositAmount
    ) {
        return new QuoteLine(
                toolId,
                quantity,
                dailyRate,
                unitAgreedAmount,
                unitDepositAmount,
                unitAgreedAmount.times(quantity),
                unitDepositAmount.times(quantity)
        );
    }
}
