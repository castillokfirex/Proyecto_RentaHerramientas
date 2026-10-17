package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Actor;

/**
 * Consulta paginada de reservas filtradas por el rol del actor (propias para CLIENTE/PROVEEDOR, todas para ADMIN)
 * y opcionalmente por estado.
 */
public interface ListReservationsQuery {

    PageResult<ReservationView> listReservations(Actor actor, ReservationStatus status, PageQuery pageQuery);
}
