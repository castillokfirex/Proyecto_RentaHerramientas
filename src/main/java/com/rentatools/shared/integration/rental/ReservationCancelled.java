package com.rentatools.shared.integration.rental;

import com.rentatools.shared.integration.IntegrationEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integracion emitido por el contexto {@code rental} cuando se cancela una reserva.
 *
 * <p><strong>Cuando se publica:</strong> Se publica cuando el cliente cancela una reserva pendiente
 * o confirmada antes de la entrega, o cuando el proveedor rechaza la reserva.</p>
 *
 * <p><strong>Quien lo consume:</strong> Es consumido por {@code billing} para anular la factura
 * {@code RENTAL} y proceder con el reembolso o liberacion de garantias correspondientes.</p>
 *
 * @param eventId Identificador unico del evento de integracion
 * @param occurredAt Instante en que ocurrio la cancelacion
 * @param reservationId Identificador unico de la reserva cancelada
 * @param previousStatus Estado en el que se encontraba la reserva antes de la cancelacion
 * @param cancelledBy Identificador del usuario que ejecuto la cancelacion
 * @param reason Motivo o justificacion de la cancelacion
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
