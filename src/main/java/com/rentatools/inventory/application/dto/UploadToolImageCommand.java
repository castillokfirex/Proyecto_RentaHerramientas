package com.rentatools.inventory.application.dto;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para subir una imagen y asociarla a la galería de una herramienta.
 */
public record UploadToolImageCommand(
        UUID toolId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        InputStream content
) {
    public UploadToolImageCommand {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
