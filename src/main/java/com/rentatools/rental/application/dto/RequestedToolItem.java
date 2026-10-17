package com.rentatools.rental.application.dto;

import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.Objects;
import java.util.UUID;

/**
 * Ítem solicitado en una cotización o creación de reserva: herramienta y cantidad de unidades.
 */
public record RequestedToolItem(
        UUID toolId,
        int quantity
) {

    public RequestedToolItem {
        Objects.requireNonNull(toolId, "toolId must not be null");
        if (quantity <= 0) {
            throw new BusinessRuleViolation("INVALID_REQUESTED_QUANTITY", "quantity must be >= 1");
        }
    }
}
