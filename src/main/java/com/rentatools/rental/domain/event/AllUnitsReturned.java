package com.rentatools.rental.domain.event;

import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando se registra la devolución (RETURN) de todas las unidades de una reserva
 * y la reserva pasa a estado RETURNED.
 */
public record AllUnitsReturned(
        UUID eventId,
        Instant occurredAt,
        ReservationId reservationId
) implements DomainEvent {

    public AllUnitsReturned {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
    }
}
