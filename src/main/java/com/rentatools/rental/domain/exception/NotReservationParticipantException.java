package com.rentatools.rental.domain.exception;

import com.rentatools.shared.domain.DomainException;
import java.util.UUID;

/**
 * Se lanza cuando un actor que no es partícipe (o no es el dueño esperado) intenta operar sobre una reserva.
 */
public final class NotReservationParticipantException extends DomainException {

    public NotReservationParticipantException(UUID reservationId, UUID userId) {
        super(
                "NOT_RESERVATION_PARTICIPANT",
                "User " + userId + " is not authorized to operate on reservation " + reservationId
        );
    }

    public NotReservationParticipantException(String message) {
        super("NOT_RESERVATION_PARTICIPANT", message);
    }
}
