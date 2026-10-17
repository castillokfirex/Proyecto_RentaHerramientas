package com.rentatools.shared.integration.rental;

import com.rentatools.shared.integration.IntegrationEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integración emitido por {@code rental} cuando se cancela una reserva.
 * Lo consume {@code billing} para anular la factura {@code RENTAL} y reembolsar lo pagado y la garantía.
 */
public record ReservationCancelled(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        String previousStatus,
        UUID cancelledBy,
        String reason
) implements IntegrationEvent {

    public ReservationCancelled {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(previousStatus, "previousStatus must not be null");
        Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
    }
}
