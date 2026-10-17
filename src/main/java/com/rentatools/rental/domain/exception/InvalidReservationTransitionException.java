package com.rentatools.rental.domain.exception;

import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta una transición de estado no permitida sobre una reserva.
 */
public final class InvalidReservationTransitionException extends DomainException {

    public InvalidReservationTransitionException(ReservationStatus from, ReservationStatus to) {
        super(
                "INVALID_RESERVATION_TRANSITION",
                "Cannot transition reservation from " + from + " to " + to
        );
    }

    public InvalidReservationTransitionException(String message) {
        super("INVALID_RESERVATION_TRANSITION", message);
    }
}
