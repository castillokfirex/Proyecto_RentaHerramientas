package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.unit.UnitStatus;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para cambiar el estado operativo de una unidad física ({@code AVAILABLE <-> MAINTENANCE -> RETIRED}).
 */
public record ChangeUnitStatusCommand(
        UUID toolId,
        UUID unitId,
        UnitStatus newStatus,
        String notes
) {
    public ChangeUnitStatusCommand {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");
    }
}
