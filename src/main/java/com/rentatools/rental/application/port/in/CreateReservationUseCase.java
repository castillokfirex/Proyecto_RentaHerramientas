package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.CreateReservationCommand;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.shared.domain.Actor;

/**
 * Caso de uso para crear una nueva reserva bloqueando las unidades operativas disponibles.
 */
public interface CreateReservationUseCase {

    ReservationView createReservation(Actor actor, CreateReservationCommand command);
}
