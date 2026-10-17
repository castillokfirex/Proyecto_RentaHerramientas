package com.rentatools.inventory.domain.model.tool;

import com.rentatools.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador fuertemente tipado para una imagen de herramienta ({@link ToolImage}).
 */
public record ToolImageId(UUID value) {

    public ToolImageId {
        Objects.requireNonNull(value, "ToolImageId value must not be null");
    }

    public static ToolImageId newId() {
        return new ToolImageId(Ids.newId());
    }

    public static ToolImageId of(UUID value) {
        return new ToolImageId(value);
    }

    public static ToolImageId of(String raw) {
        return new ToolImageId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
