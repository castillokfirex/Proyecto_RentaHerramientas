package com.rentatools.rental.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Datos básicos de los partícipes de una reserva expuestos por el contexto {@code rental}
 * para verificación de permisos en otros contextos acotados (como {@code billing}).
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
