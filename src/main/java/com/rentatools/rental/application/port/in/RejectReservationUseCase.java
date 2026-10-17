package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Caso de uso para que el proveedor propietario rechace una reserva en estado PENDING.
 */
public interface RejectReservationUseCase {

    ReservationView rejectReservation(Actor actor, UUID reservationId, String reason);
}
