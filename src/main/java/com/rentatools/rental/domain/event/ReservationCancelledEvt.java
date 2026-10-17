package com.rentatools.rental.domain.event;

import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando el cliente cancela una reserva.
 */
public record ReservationCancelledEvt(
        UUID eventId,
        Instant occurredAt,
        ReservationId reservationId,
        ReservationStatus previousStatus,
        String reason
) implements DomainEvent {

    public ReservationCancelledEvt {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(previousStatus, "previousStatus must not be null");
    }
}
