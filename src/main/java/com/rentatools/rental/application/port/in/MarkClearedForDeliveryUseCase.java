package com.rentatools.rental.application.port.in;

import java.util.UUID;

/**
 * Caso de uso interno para habilitar la entrega de una reserva al confirmarse el pago y la garantía en facturación.
 */
public interface MarkClearedForDeliveryUseCase {

    void markClearedForDelivery(UUID reservationId);
}
