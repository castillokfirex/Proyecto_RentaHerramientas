package com.rentatools.rental.domain.model.reservation;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador del cliente titular de una reserva.
 */
public record ClientId(UUID value) {

    public ClientId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ClientId of(UUID value) {
        return new ClientId(value);
    }
}
