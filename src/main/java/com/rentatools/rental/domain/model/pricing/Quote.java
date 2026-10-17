package com.rentatools.rental.domain.model.pricing;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Money;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de cotización de una reserva sobre un rango de fechas.
 */
public record Quote(
        DateRange dateRange,
        long days,
        List<QuoteLine> lines,
        Money totalAgreedAmount,
        Money totalDepositAmount
) {

    public Quote {
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        if (lines.isEmpty()) {
            throw new BusinessRuleViolation("EMPTY_QUOTE_LINES", "Quote must contain at least one line");
        }
        lines = List.copyOf(lines);
        Objects.requireNonNull(totalAgreedAmount, "totalAgreedAmount must not be null");
        Objects.requireNonNull(totalDepositAmount, "totalDepositAmount must not be null");
    }

    public static Quote of(DateRange dateRange, List<QuoteLine> lines) {
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        Money totalAgreed = Money.ZERO;
        Money totalDeposit = Money.ZERO;
        for (QuoteLine line : lines) {
            Objects.requireNonNull(line, "line must not be null");
            totalAgreed = totalAgreed.plus(line.lineAgreedAmount());
            totalDeposit = totalDeposit.plus(line.lineDepositAmount());
        }
        return new Quote(dateRange, dateRange.days(), lines, totalAgreed, totalDeposit);
    }
}
