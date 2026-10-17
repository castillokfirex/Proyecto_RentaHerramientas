package com.rentatools.inventory.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando para crear una nueva categoría del catálogo.
 */
public record CreateCategoryCommand(
        UUID parentId,
        String name,
        String description
) {
    public CreateCategoryCommand {
        Objects.requireNonNull(name, "name must not be null");
    }
}
