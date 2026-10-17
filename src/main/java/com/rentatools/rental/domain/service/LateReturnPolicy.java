package com.rentatools.rental.domain.service;

import com.rentatools.shared.domain.DateRange;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Servicio de dominio para calcular los días de mora en la devolución de una unidad:
 * {@code lateDays = max(0, fecha_inspeccion_retorno - end_date)}.
 */
public class LateReturnPolicy {

    public LateReturnPolicy() {
    }

    /**
     * Calcula los días de retraso entre la fecha de fin pactada ({@code endDate}) y la fecha local
     * de la inspección de devolución ({@code returnInspectionDate}).
     */
    public int calculateLateDays(LocalDate endDate, LocalDate returnInspectionDate) {
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(returnInspectionDate, "returnInspectionDate must not be null");
        long diff = ChronoUnit.DAYS.between(endDate, returnInspectionDate);
        return (int) Math.max(0L, diff);
    }

    /**
     * Calcula los días de retraso a partir del rango de fechas de la reserva y la fecha local de devolución.
     */
    public int calculateLateDays(DateRange reservationRange, LocalDate returnInspectionDate) {
        Objects.requireNonNull(reservationRange, "reservationRange must not be null");
        return calculateLateDays(reservationRange.end(), returnInspectionDate);
    }

    /**
     * Calcula los días de retraso convirtiendo el instante {@code returnPerformedAt} a {@link LocalDate}
     * según la zona horaria indicada.
     */
    public int calculateLateDays(LocalDate endDate, Instant returnPerformedAt, ZoneId zoneId) {
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(returnPerformedAt, "returnPerformedAt must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        LocalDate returnDate = LocalDate.ofInstant(returnPerformedAt, zoneId);
        return calculateLateDays(endDate, returnDate);
    }

    /**
     * Calcula los días de retraso usando la zona horaria del {@link Clock} suministrado.
     */
    public int calculateLateDays(DateRange reservationRange, Instant returnPerformedAt, Clock clock) {
        Objects.requireNonNull(reservationRange, "reservationRange must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        return calculateLateDays(reservationRange.end(), returnPerformedAt, clock.getZone());
    }
}
