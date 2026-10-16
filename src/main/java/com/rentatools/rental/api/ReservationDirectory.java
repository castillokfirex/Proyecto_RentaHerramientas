package com.rentatools.rental.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto público del contexto {@code rental} consumido por {@code billing}
 * para consultar el cliente y proveedor partícipes de una reserva.
 */
public interface ReservationDirectory {

    Optional<ReservationParticipants> findParticipants(UUID reservationId);
}
