package com.rentatools.rental.domain.model.reservation;

/**
 * Estados del ciclo de vida de una reserva según `V3__rental.sql` y `PLAN.md` §3.2.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    READY_FOR_PICKUP,
    ACTIVE,
    RETURNED,
    CLOSED,
    REJECTED,
    CANCELLED;

    /**
     * Indica si la reserva ocupa unidades en caso de solapamiento de fechas.
     */
    public boolean occupiesUnitsOnOverlap() {
        return this == PENDING
                || this == CONFIRMED
                || this == READY_FOR_PICKUP
                || this == ACTIVE;
    }

    /**
     * Indica si el cliente puede cancelar la reserva desde este estado.
     */
    public boolean isCancellableByClient() {
        return this == PENDING
                || this == CONFIRMED
                || this == READY_FOR_PICKUP;
    }
}
