package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.application.dto.ToolUnitView;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de respuesta para una unidad física ({@code ToolUnit}).
 */
public record ToolUnitResponse(
        UUID id,
        UUID toolId,
        String serialNumber,
        UnitStatus status,
        LocalDate acquiredOn,
        String notes,
        Instant createdAt
) {
    public static ToolUnitResponse from(ToolUnitView view) {
        return new ToolUnitResponse(
                view.id(),
                view.toolId(),
                view.serialNumber(),
                view.status(),
                view.acquiredOn(),
                view.notes(),
                view.createdAt()
        );
    }
}
