package com.rentatools.inventory.domain.model.tool;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador fuertemente tipado del proveedor propietario de una herramienta.
 */
public record ProviderId(UUID value) {

    public ProviderId {
        Objects.requireNonNull(value, "ProviderId value must not be null");
    }

    public static ProviderId of(UUID value) {
        return new ProviderId(value);
    }

    public static ProviderId of(String raw) {
        return new ProviderId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
