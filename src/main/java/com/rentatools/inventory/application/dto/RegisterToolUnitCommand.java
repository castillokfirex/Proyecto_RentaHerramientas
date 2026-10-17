package com.rentatools.inventory.application.dto;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para registrar una nueva unidad física ({@code ToolUnit}) asociada a una herramienta.
 */
public record RegisterToolUnitCommand(
        UUID toolId,
        String serialNumber,
        LocalDate acquiredOn,
        String notes
) {
    public RegisterToolUnitCommand {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(serialNumber, "serialNumber must not be null");
    }
}
