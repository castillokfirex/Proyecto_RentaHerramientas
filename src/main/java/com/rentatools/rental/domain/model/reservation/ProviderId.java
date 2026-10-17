package com.rentatools.rental.domain.model.reservation;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador del proveedor propietario de las herramientas reservadas.
 */
public record ProviderId(UUID value) {

    public ProviderId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ProviderId of(UUID value) {
        return new ProviderId(value);
    }
}
