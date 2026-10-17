package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Caso de uso para que el cliente titular cancele una reserva antes de la entrega.
 */
public interface CancelReservationUseCase {

    ReservationView cancelReservation(Actor actor, UUID reservationId, String reason);
}
