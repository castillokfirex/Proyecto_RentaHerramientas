package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.QuoteReservationCommand;
import com.rentatools.rental.domain.model.pricing.Quote;
import com.rentatools.shared.domain.Actor;

/**
 * Caso de uso para calcular una cotización previa de reserva sin persistirla.
 */
public interface QuoteReservationUseCase {

    Quote quoteReservation(Actor actor, QuoteReservationCommand command);
}
