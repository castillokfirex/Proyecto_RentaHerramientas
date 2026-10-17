package com.rentatools.rental.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando una reserva intenta incluir unidades pertenecientes a más de un proveedor.
 */
public final class MixedProvidersException extends DomainException {

    public MixedProvidersException() {
        super("MIXED_PROVIDERS", "All units in a reservation must belong to a single provider");
    }

    public MixedProvidersException(String message) {
        super("MIXED_PROVIDERS", message);
    }
}
