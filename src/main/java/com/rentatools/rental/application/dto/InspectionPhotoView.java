package com.rentatools.rental.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista de una fotografía adjunta a una inspección.
 */
public record InspectionPhotoView(
        UUID id,
        UUID inspectionId,
        String storageKey,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public InspectionPhotoView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }
}
