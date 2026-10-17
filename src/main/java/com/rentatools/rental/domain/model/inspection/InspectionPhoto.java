package com.rentatools.rental.domain.model.inspection;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evidencia fotográfica adjunta a una inspección (`inspection_photos`).
 */
public record InspectionPhoto(
        UUID id,
        String storageKey,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {

    public static final long MAX_SIZE_BYTES = 5_242_880L; // 5 MB
    private static final int MAX_STORAGE_KEY_LENGTH = 255;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public InspectionPhoto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");

        String trimmedKey = storageKey.trim();
        if (trimmedKey.isEmpty() || trimmedKey.length() > MAX_STORAGE_KEY_LENGTH) {
            throw new BusinessRuleViolation(
                    "INVALID_PHOTO_STORAGE_KEY",
                    "Photo storageKey must have between 1 and " + MAX_STORAGE_KEY_LENGTH + " characters"
            );
        }
        String normalizedType = contentType.trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw new BusinessRuleViolation(
                    "INVALID_PHOTO_CONTENT_TYPE",
                    "Unsupported photo content type: " + contentType + ". Allowed: image/jpeg, image/png, image/webp"
            );
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessRuleViolation(
                    "INVALID_PHOTO_SIZE",
                    "Photo sizeBytes must be > 0 and <= " + MAX_SIZE_BYTES
            );
        }

        storageKey = trimmedKey;
        contentType = normalizedType;
    }

    public static InspectionPhoto create(
            String storageKey,
            String contentType,
            long sizeBytes,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new InspectionPhoto(
                Ids.newId(clock),
                storageKey,
                contentType,
                sizeBytes,
                clock.instant()
        );
    }
}
