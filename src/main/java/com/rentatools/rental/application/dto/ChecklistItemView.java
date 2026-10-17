package com.rentatools.rental.application.dto;

import java.util.Objects;

/**
 * Vista de un ítem de checklist para la administración y configuración de inspecciones.
 */
public record ChecklistItemView(
        int id,
        String code,
        String label,
        boolean active
) {
    public ChecklistItemView {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }
}
