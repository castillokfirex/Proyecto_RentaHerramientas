package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload opcional con el motivo de rechazo o cancelación de una reserva.
 */
public record ReasonRequest(
        @Size(max = 300, message = "reason cannot exceed 300 characters")
        String reason
) {
}
