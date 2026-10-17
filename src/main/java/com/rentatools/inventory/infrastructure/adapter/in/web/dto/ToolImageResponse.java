package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.application.dto.ToolImageView;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para una imagen de herramienta.
 */
public record ToolImageResponse(
        UUID id,
        int position,
        boolean cover,
        String storageKey,
        String url,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public static ToolImageResponse from(ToolImageView view) {
        return new ToolImageResponse(
                view.id(),
                view.position(),
                view.position() == 0,
                view.storageKey(),
                "/api/v1/media/tools/" + view.storageKey(),
                view.contentType(),
                view.sizeBytes(),
                view.uploadedAt()
        );
    }
}
