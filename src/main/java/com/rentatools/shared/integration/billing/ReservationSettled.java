package com.rentatools.shared.integration.billing;

import com.rentatools.shared.integration.IntegrationEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integración emitido por Billing cuando se liquida por completo una reserva
 * tras su devolución.
 */
public record ReservationSettled(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        BigDecimal depositRefunded,
        BigDecimal extraCharges
) implements IntegrationEvent {

    public ReservationSettled {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(depositRefunded, "depositRefunded must not be null");
        Objects.requireNonNull(extraCharges, "extraCharges must not be null");
    }
}
