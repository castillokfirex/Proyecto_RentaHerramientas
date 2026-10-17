package com.rentatools.rental.domain.model.reservation;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de una unidad física de inventario (tool_units.id) referenciada desde rental.
 */
public record UnitId(UUID value) {

    public UnitId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static UnitId of(UUID value) {
        return new UnitId(value);
    }
}
