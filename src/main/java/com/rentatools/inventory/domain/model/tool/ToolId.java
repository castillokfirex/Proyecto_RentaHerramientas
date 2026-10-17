package com.rentatools.inventory.domain.model.tool;

import com.rentatools.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador fuertemente tipado para el agregado {@link Tool}.
 */
public record ToolId(UUID value) {

    public ToolId {
        Objects.requireNonNull(value, "ToolId value must not be null");
    }

    public static ToolId newId() {
        return new ToolId(Ids.newId());
    }

    public static ToolId of(UUID value) {
        return new ToolId(value);
    }

    public static ToolId of(String raw) {
        return new ToolId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
