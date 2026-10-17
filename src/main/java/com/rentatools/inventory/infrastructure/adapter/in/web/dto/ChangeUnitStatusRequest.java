package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.domain.model.unit.UnitStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para cambiar el estado operativo de una unidad física.
 */
public record ChangeUnitStatusRequest(
        @NotNull(message = "status is required")
        UnitStatus status,
        @Size(max = 500, message = "notes must not exceed 500 characters")
        String notes
) {
}
