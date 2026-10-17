package com.rentatools.shared.integration.rental;

import com.rentatools.shared.integration.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integración emitido por {@code rental} cuando se completa la inspección de devolución
 * de la última unidad de una reserva. Lo consume {@code billing} para abrir la factura {@code SETTLEMENT}
 * en borrador con los cargos de mora correspondientes.
 */
public record ReservationReturned(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID registeredBy,
        List<ReturnedUnit> units
) implements IntegrationEvent {

    public ReservationReturned {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(registeredBy, "registeredBy must not be null");
        Objects.requireNonNull(units, "units must not be null");
        units = List.copyOf(units);
    }

    public record ReturnedUnit(
            UUID reservationUnitId,
            String toolName,
            LocalDate returnDate,
            int lateDays,
            BigDecimal dailyRate,
            boolean damageReported,
            boolean lost
    ) {
        public ReturnedUnit {
            Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
            Objects.requireNonNull(toolName, "toolName must not be null");
            Objects.requireNonNull(returnDate, "returnDate must not be null");
            Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        }
    }
}
