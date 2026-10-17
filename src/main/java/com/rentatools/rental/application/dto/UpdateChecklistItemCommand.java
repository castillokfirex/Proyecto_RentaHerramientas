package com.rentatools.rental.application.dto;

import java.util.Objects;

/**
 * Comando para actualizar la etiqueta y estado activo/inactivo de un ítem de checklist.
 */
public record UpdateChecklistItemCommand(
        String label,
        boolean active
) {
    public UpdateChecklistItemCommand {
        Objects.requireNonNull(label, "label must not be null");
    }
}
