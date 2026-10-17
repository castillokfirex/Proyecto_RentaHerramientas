package com.rentatools.inventory.domain.model.unit;

import java.util.Objects;

/**
 * Value Object que representa el número de serie físico de una unidad de herramienta.
 */
public record SerialNumber(String value) {

    private static final int MAX_LENGTH = 80;

    public SerialNumber {
        Objects.requireNonNull(value, "SerialNumber value must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("SerialNumber must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("SerialNumber must not exceed " + MAX_LENGTH + " characters");
        }
        value = trimmed;
    }

    public static SerialNumber of(String raw) {
        return new SerialNumber(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
