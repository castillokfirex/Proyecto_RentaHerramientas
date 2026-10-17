package com.rentatools.inventory.domain.model.category;

import com.rentatools.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador fuertemente tipado para el agregado {@link Category}.
 */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "CategoryId value must not be null");
    }

    public static CategoryId newId() {
        return new CategoryId(Ids.newId());
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    public static CategoryId of(String raw) {
        return new CategoryId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
