package com.rentatools.rental.application.dto;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para subir y adjuntar una fotografía a una inspección realizada.
 */
public record AttachInspectionPhotoCommand(
        UUID inspectionId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        InputStream content
) {
    public AttachInspectionPhotoCommand {
        Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
