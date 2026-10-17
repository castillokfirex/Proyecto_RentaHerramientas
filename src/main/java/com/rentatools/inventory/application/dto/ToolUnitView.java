package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura de una unidad física ({@link ToolUnit}).
 */
public record ToolUnitView(
        UUID id,
        UUID toolId,
        String serialNumber,
        UnitStatus status,
        LocalDate acquiredOn,
        String notes,
        Instant createdAt
) {
    public ToolUnitView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static ToolUnitView from(ToolUnit unit) {
        return new ToolUnitView(
                unit.id().value(),
                unit.toolId().value(),
                unit.serialNumber().value(),
                unit.status(),
                unit.acquiredOn(),
                unit.notes(),
                unit.createdAt()
        );
    }
}
