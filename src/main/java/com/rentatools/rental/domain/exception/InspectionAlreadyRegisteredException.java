package com.rentatools.rental.domain.exception;

import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando ya existe una inspección del mismo tipo (DELIVERY o RETURN) para una unidad de reserva.
 */
public final class InspectionAlreadyRegisteredException extends DomainException {

    public InspectionAlreadyRegisteredException(ReservationUnitId reservationUnitId, InspectionType type) {
        super(
                "INSPECTION_ALREADY_REGISTERED",
                "An inspection of type " + type + " is already registered for reservation unit " + reservationUnitId.value()
        );
    }

    public InspectionAlreadyRegisteredException(String message) {
        super("INSPECTION_ALREADY_REGISTERED", message);
    }
}
