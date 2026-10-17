package com.rentatools.inventory.domain.event;

import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando cambia el estado operativo de una unidad física ({@code ToolUnit}).
 */
public record UnitStatusChanged(
        UUID eventId,
        Instant occurredAt,
        ToolUnitId unitId,
        ToolId toolId,
        UnitStatus previousStatus,
        UnitStatus newStatus
) implements DomainEvent {

    public UnitStatusChanged {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(previousStatus, "previousStatus must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");
    }
}
