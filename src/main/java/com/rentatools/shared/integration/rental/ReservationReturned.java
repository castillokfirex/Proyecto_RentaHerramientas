package com.rentatools.shared.integration.rental;

import com.rentatools.shared.integration.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de integracion emitido por el contexto {@code rental} al completarse la inspeccion
 * de devolucion de todas las unidades fisicas pertenecientes a una reserva.
 *
 * <p><strong>Cuando se publica:</strong> Se publica cuando la ultima inspeccion de tipo {@code RETURN}
 * queda registrada y la reserva transiciona al estado {@code RETURNED}.</p>
 *
 * <p><strong>Quien lo consume:</strong> Es consumido por el contexto {@code billing} para aperturar
 * la factura de liquidacion final ({@code SETTLEMENT}) en estado borrador, calculando cargos
 * de mora por dias de retraso o retenciones de garantia por danos o perdidas.</p>
 *
 * @param eventId Identificador unico del evento de integracion
 * @param occurredAt Instante en que se completo la devolucion de las herramientas
 * @param reservationId Identificador unico de la reserva devuelta
 * @param registeredBy Identificador del usuario (operador/proveedor) que registro la devolucion
 * @param units Lista inmutable con el detalle del estado de devolucion de cada unidad
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

    /**
     * Detalle del retorno de una unidad fisica especifica de la reserva.
     *
     * @param reservationUnitId Identificador de la unidad en la reserva
     * @param toolName Nombre comercial de la herramienta
     * @param returnDate Fecha efectiva en la que se realizo la devolucion fisica
     * @param lateDays Cantidad de dias de retraso con respecto a la fecha final pactada (>= 0)
     * @param dailyRate Tarifa diaria base aplicable para el calculo de penalizaciones por mora
     * @param damageReported Indica si se reportaron averias o desperfectos en la inspeccion
     * @param lost Indica si la unidad fue declarada perdida o no devuelta
     */
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
