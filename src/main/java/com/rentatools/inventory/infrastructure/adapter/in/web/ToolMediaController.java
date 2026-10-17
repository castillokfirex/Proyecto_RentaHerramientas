package com.rentatools.inventory.infrastructure.adapter.in.web;

import com.rentatools.shared.application.port.out.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST público para servir archivos de imágenes de herramientas almacenados en {@link FileStorage}.
 */
@RestController
@RequestMapping("/api/v1/media/tools")
public class ToolMediaController {

    private final FileStorage fileStorage;

    public ToolMediaController(FileStorage fileStorage) {
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
    }

    @GetMapping("/{*storageKey}")
    public ResponseEntity<byte[]> getToolMedia(@PathVariable("storageKey") String storageKey) throws IOException {
        String normalizedKey = storageKey.startsWith("/") ? storageKey.substring(1) : storageKey;
        try (InputStream stream = fileStorage.open(normalizedKey)) {
            byte[] content = stream.readAllBytes();
            MediaType mediaType = resolveMediaType(normalizedKey);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(content);
        }
    }

    private static MediaType resolveMediaType(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
