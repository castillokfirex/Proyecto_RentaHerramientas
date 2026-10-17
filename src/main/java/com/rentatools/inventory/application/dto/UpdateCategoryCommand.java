package com.rentatools.inventory.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando para actualizar una categoría existente del catálogo.
 */
public record UpdateCategoryCommand(
        UUID categoryId,
        UUID parentId,
        String name,
        String description,
        boolean active
) {
    public UpdateCategoryCommand {
        Objects.requireNonNull(categoryId, "categoryId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
