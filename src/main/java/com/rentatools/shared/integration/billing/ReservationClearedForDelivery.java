package com.rentatools.shared.integration.billing;

import com.rentatools.shared.integration.IntegrationEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integración emitido por Billing cuando la factura RENTAL está pagada
 * y la garantía está cubierta, habilitando la entrega de herramientas.
 */
public record ReservationClearedForDelivery(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId
) implements IntegrationEvent {

    public ReservationClearedForDelivery {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
    }
}
