package com.rentatools.shared.integration.rental;

import com.rentatools.shared.integration.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integración emitido por {@code rental} cuando el proveedor confirma una reserva.
 * Lo consume {@code billing} para emitir la factura {@code RENTAL} y abrir la garantía {@code SecurityDeposit}.
 */
public record ReservationConfirmed(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        String reservationCode,
        UUID clientId,
        UUID providerId,
        LocalDate startDate,
        LocalDate endDate,
        UUID confirmedBy,
        List<Line> lines
) implements IntegrationEvent {

    public ReservationConfirmed {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(reservationCode, "reservationCode must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(confirmedBy, "confirmedBy must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        lines = List.copyOf(lines);
    }

    public record Line(
            UUID reservationUnitId,
            String toolName,
            String serialNumber,
            BigDecimal agreedAmount,
            BigDecimal depositAmount
    ) {
        public Line {
            Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
            Objects.requireNonNull(toolName, "toolName must not be null");
            Objects.requireNonNull(serialNumber, "serialNumber must not be null");
            Objects.requireNonNull(agreedAmount, "agreedAmount must not be null");
            Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        }
    }
}
