package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload de entrada para actualizar una categoría del catálogo.
 */
public record UpdateCategoryRequest(
        UUID parentId,
        @NotBlank(message = "Category name is required")
        @Size(max = 80, message = "Category name must not exceed 80 characters")
        String name,
        @Size(max = 500, message = "Category description must not exceed 500 characters")
        String description,
        Boolean active
) {
    public boolean isActiveOrDefault() {
        return active == null || active;
    }
}
