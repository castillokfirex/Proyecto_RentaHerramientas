package com.rentatools.shared.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Value Object que representa un rango de fechas inclusivo [start, end].
 */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (end.isBefore(start)) {
            throw new BusinessRuleViolation("DATE_RANGE_INVALID", "end < start");
        }
    }

    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start, end);
    }

    /**
     * Número total de días incluidos en el rango (ambos extremos inclusivos).
     */
    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * Determina si este rango de fechas se solapa con otro rango (extremos inclusivos).
     */
    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other, "other must not be null");
        return !start.isAfter(other.end) && !other.start.isAfter(end);
    }

    /**
     * Determina si una fecha está contenida dentro del rango (inclusivo).
     */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
