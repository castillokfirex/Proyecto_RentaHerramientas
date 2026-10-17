package com.rentatools.rental.application.port.in;

import java.util.UUID;

/**
 * Caso de uso interno para cerrar una reserva al completarse su liquidación final en facturación.
 */
public interface CloseReservationUseCase {

    void closeReservation(UUID reservationId);
}
