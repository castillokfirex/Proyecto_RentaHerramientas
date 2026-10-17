package com.rentatools.rental.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Datos basicos de los participantes asociados a una reserva de alquiler.
 *
 * @param reservationId Identificador unico de la reserva
 * @param code Codigo alfanumerico legible de la reserva (ej. RES-2026-0001)
 * @param clientId Identificador unico del usuario cliente
 * @param providerId Identificador unico del usuario proveedor
 */
public record ReservationParticipants(
        UUID reservationId,
        String code,
        UUID clientId,
        UUID providerId
) {

    public ReservationParticipants {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
    }
}
