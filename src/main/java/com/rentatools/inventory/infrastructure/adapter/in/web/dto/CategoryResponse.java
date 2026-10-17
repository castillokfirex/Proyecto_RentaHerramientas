package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.application.dto.CategoryView;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para una categoría del catálogo.
 */
public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String description,
        boolean active,
        Instant createdAt
) {
    public static CategoryResponse from(CategoryView view) {
        return new CategoryResponse(
                view.id(),
                view.parentId(),
                view.name(),
                view.description(),
                view.active(),
                view.createdAt()
        );
    }
}
