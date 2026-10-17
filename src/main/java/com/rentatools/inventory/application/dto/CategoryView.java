package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.category.Category;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura de una categoría del catálogo.
 */
public record CategoryView(
        UUID id,
        UUID parentId,
        String name,
        String description,
        boolean active,
        Instant createdAt
) {
    public CategoryView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static CategoryView from(Category category) {
        return new CategoryView(
                category.id().value(),
                category.parentId() != null ? category.parentId().value() : null,
                category.name(),
                category.description(),
                category.active(),
                category.createdAt()
        );
    }
}
