package com.rentatools.inventory.domain.model.tool;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Entidad interna del agregado {@link Tool} que representa una imagen almacenada en el catálogo.
 *
 * <p>Invariantes según {@code V2__inventory.sql}:
 * <ul>
 *   <li>{@code position} entre 0 y 9 ({@code position == 0} es la imagen de portada).</li>
 *   <li>{@code contentType} en {@code image/jpeg}, {@code image/png} o {@code image/webp}.</li>
 *   <li>{@code sizeBytes} entre 1 y 5,242,880 bytes (5 MB).</li>
 *   <li>{@code storageKey} no vacío y máximo 500 caracteres.</li>
 * </ul>
 */
public final class ToolImage {

    public static final int MAX_POSITION = 9;
    public static final long MAX_SIZE_BYTES = 5_242_880L;
    private static final int MAX_STORAGE_KEY_LENGTH = 500;
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final ToolImageId id;
    private int position;
    private final String storageKey;
    private final String contentType;
    private final long sizeBytes;
    private final Instant uploadedAt;

    public ToolImage(
            ToolImageId id,
            int position,
            String storageKey,
            String contentType,
            long sizeBytes,
            Instant uploadedAt
    ) {
        this.id = Objects.requireNonNull(id, "ToolImage id must not be null");
        validatePosition(position);
        this.position = position;
        this.storageKey = validateStorageKey(storageKey);
        this.contentType = validateContentType(contentType);
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Image sizeBytes must be between 1 and " + MAX_SIZE_BYTES + ", got: " + sizeBytes
            );
        }
        this.sizeBytes = sizeBytes;
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }

    void updatePosition(int newPosition) {
        validatePosition(newPosition);
        this.position = newPosition;
    }

    private static void validatePosition(int position) {
        if (position < 0 || position > MAX_POSITION) {
            throw new IllegalArgumentException("Image position must be between 0 and " + MAX_POSITION + ", got: " + position);
        }
    }

    private static String validateStorageKey(String rawKey) {
        Objects.requireNonNull(rawKey, "storageKey must not be null");
        String trimmed = rawKey.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        if (trimmed.length() > MAX_STORAGE_KEY_LENGTH) {
            throw new IllegalArgumentException("storageKey must not exceed " + MAX_STORAGE_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String validateContentType(String rawContentType) {
        Objects.requireNonNull(rawContentType, "contentType must not be null");
        String normalized = rawContentType.trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported image contentType: " + rawContentType);
        }
        return normalized;
    }

    public boolean isCover() {
        return position == 0;
    }

    public ToolImageId id() {
        return id;
    }

    public int position() {
        return position;
    }

    public String storageKey() {
        return storageKey;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public Instant uploadedAt() {
        return uploadedAt;
    }
}
