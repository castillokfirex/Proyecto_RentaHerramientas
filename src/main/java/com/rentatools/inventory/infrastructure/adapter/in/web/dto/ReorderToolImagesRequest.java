package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Payload de entrada para reordenar las imágenes de una herramienta.
 */
public record ReorderToolImagesRequest(
        @NotEmpty(message = "orderedImageIds must not be empty")
        List<UUID> orderedImageIds
) {
}
