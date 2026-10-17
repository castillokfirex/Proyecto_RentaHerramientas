package com.rentatools.inventory.domain.model.unit;

import com.rentatools.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador fuertemente tipado para el agregado {@link ToolUnit}.
 */
public record ToolUnitId(UUID value) {

    public ToolUnitId {
        Objects.requireNonNull(value, "ToolUnitId value must not be null");
    }

    public static ToolUnitId newId() {
        return new ToolUnitId(Ids.newId());
    }

    public static ToolUnitId of(UUID value) {
        return new ToolUnitId(value);
    }

    public static ToolUnitId of(String raw) {
        return new ToolUnitId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
