package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.tool.ToolImage;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura de una imagen perteneciente a una herramienta.
 */
public record ToolImageView(
        UUID id,
        int position,
        String storageKey,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public ToolImageView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }

    public static ToolImageView from(ToolImage image) {
        return new ToolImageView(
                image.id().value(),
                image.position(),
                image.storageKey(),
                image.contentType(),
                image.sizeBytes(),
                image.uploadedAt()
        );
    }
}
