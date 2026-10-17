package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload de entrada para registrar una nueva unidad física de una herramienta.
 */
public record RegisterToolUnitRequest(
        @NotBlank(message = "serialNumber is required")
        @Size(max = 80, message = "serialNumber must not exceed 80 characters")
        String serialNumber,
        LocalDate acquiredOn,
        @Size(max = 500, message = "notes must not exceed 500 characters")
        String notes
) {
}
