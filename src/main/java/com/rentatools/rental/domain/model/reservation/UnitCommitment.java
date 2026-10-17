package com.rentatools.rental.domain.model.reservation;

import com.rentatools.shared.domain.DateRange;
import java.util.Objects;

/**
 * Representa un compromiso de reserva existente sobre una unidad física (`UnitId`),
 * utilizado por {@link com.rentatools.rental.domain.service.UnitAllocator} para determinar
 * si la unidad está ocupada por solapamiento de fechas o por devolución demorada.
 *
 * @param unitId                     identificador de la unidad física
 * @param status                     estado actual de la reserva a la que pertenece
 * @param dateRange                  rango de fechas de la reserva
 * @param returnInspectionRegistered {@code true} si la unidad ya cuenta con inspección {@code RETURN}
 */
public record UnitCommitment(
        UnitId unitId,
        ReservationStatus status,
        DateRange dateRange,
        boolean returnInspectionRegistered
) {

    public UnitCommitment {
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");
    }

    public static UnitCommitment of(
            UnitId unitId,
            ReservationStatus status,
            DateRange dateRange,
            boolean returnInspectionRegistered
    ) {
        return new UnitCommitment(unitId, status, dateRange, returnInspectionRegistered);
    }

    public static UnitCommitment activeUnreturned(UnitId unitId, DateRange dateRange) {
        return new UnitCommitment(unitId, ReservationStatus.ACTIVE, dateRange, false);
    }

    public static UnitCommitment forReservation(UnitId unitId, ReservationStatus status, DateRange dateRange) {
        return new UnitCommitment(unitId, status, dateRange, status == ReservationStatus.RETURNED || status == ReservationStatus.CLOSED);
    }

    /**
     * Determina si este compromiso mantiene ocupada la unidad frente a un nuevo rango de fechas solicitado:
     * <ol>
     *   <li>La reserva está en {@code PENDING / CONFIRMED / READY_FOR_PICKUP / ACTIVE} y sus fechas se solapan
     *       con {@code requestedRange}, o</li>
     *   <li>La reserva está en {@code ACTIVE} y la unidad aún no tiene registrada su inspección {@code RETURN}
     *       (devolución demorada / tardía).</li>
     * </ol>
     */
    public boolean blocks(DateRange requestedRange) {
        Objects.requireNonNull(requestedRange, "requestedRange must not be null");
        if (status == ReservationStatus.ACTIVE && !returnInspectionRegistered) {
            return true;
        }
        return status.occupiesUnitsOnOverlap() && dateRange.overlaps(requestedRange);
    }
}
