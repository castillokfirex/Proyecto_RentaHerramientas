package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Caso de uso para que el proveedor propietario confirme una reserva en estado PENDING.
 */
public interface ConfirmReservationUseCase {

    ReservationView confirmReservation(Actor actor, UUID reservationId);
}
