package com.rentatools.rental.domain.event;

import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando se crea una nueva reserva en estado PENDING.
 */
public record ReservationCreated(
        UUID eventId,
        Instant occurredAt,
        ReservationId reservationId,
        ReservationCode code,
        ClientId clientId,
        ProviderId providerId,
        DateRange dateRange
) implements DomainEvent {

    public ReservationCreated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");
    }
}
