package com.rentatools.rental.domain.exception;

import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta registrar una inspección RETURN sin que exista previamente una inspección DELIVERY
 * para la misma unidad de reserva.
 */
public final class DeliveryNotRegisteredException extends DomainException {

    public DeliveryNotRegisteredException(ReservationUnitId reservationUnitId) {
        super(
                "DELIVERY_NOT_REGISTERED",
                "Cannot register RETURN inspection without a prior DELIVERY inspection for reservation unit " + reservationUnitId.value()
        );
    }

    public DeliveryNotRegisteredException(String message) {
        super("DELIVERY_NOT_REGISTERED", message);
    }
}
