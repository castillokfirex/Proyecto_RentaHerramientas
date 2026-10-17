package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Consulta para obtener el detalle de una reserva verificando permisos de partícipe o ADMIN.
 */
public interface GetReservationQuery {

    ReservationView getReservation(Actor actor, UUID reservationId);
}
