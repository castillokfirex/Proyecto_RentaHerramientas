package com.rentatools.shared.integration.rental;

import com.rentatools.shared.integration.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integracion emitido por el contexto {@code rental} cuando el proveedor confirma una reserva.
 *
 * <p><strong>Cuando se publica:</strong> Se publica sincronamente al ejecutar el caso de uso
 * de confirmacion de reserva (transicion de {@code PENDING} a {@code CONFIRMED}).</p>
 *
 * <p><strong>Quien lo consume:</strong> Es consumido por el contexto {@code billing} para generar
 * automaticamente la factura inicial de alquiler ({@code RENTAL}) y aperturar el registro del
 * deposito en garantia ({@code SecurityDeposit}).</p>
 *
 * @param eventId Identificador unico del evento de integracion
 * @param occurredAt Instante exacto en que ocurrio la confirmacion de la reserva
 * @param reservationId Identificador unico de la reserva confirmada
 * @param reservationCode Codigo alfanumerico legible de la reserva (ej. RES-2026-0001)
 * @param clientId Identificador unico del cliente que alquila los equipos
 * @param providerId Identificador unico del proveedor propietario de los equipos
 * @param startDate Fecha de inicio del periodo de alquiler acordado
 * @param endDate Fecha de fin del periodo de alquiler acordado
 * @param confirmedBy Identificador del usuario que confirmo la reserva
 * @param lines Lista inmutable de lineas con el desglose de unidades asignadas y montos acordados
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

    /**
     * Desglose de cada unidad de herramienta asignada a la reserva confirmada.
     *
     * @param reservationUnitId Identificador de la asignacion de la unidad en la reserva
     * @param toolName Nombre comercial de la herramienta
     * @param serialNumber Numero de serie fisico de la unidad entregada
     * @param agreedAmount Importe pactado por el concepto de alquiler de esta unidad
     * @param depositAmount Monto individual requerido como garantia de esta unidad
     */
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
