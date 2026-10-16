package com.rentatools.shared.application.port.out;

import java.util.Objects;

/**
 * Metadatos de un archivo almacenado mediante {@link FileStorage}.
 */
public record StoredFile(String storageKey, String contentType, long sizeBytes) {

    public StoredFile {
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
    }
}
