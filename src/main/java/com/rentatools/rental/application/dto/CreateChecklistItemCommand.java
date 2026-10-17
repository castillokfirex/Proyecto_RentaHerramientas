package com.rentatools.rental.application.dto;

import java.util.Objects;

/**
 * Comando para registrar un nuevo ítem en el catálogo de checklist.
 */
public record CreateChecklistItemCommand(
        String code,
        String label
) {
    public CreateChecklistItemCommand {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }
}
