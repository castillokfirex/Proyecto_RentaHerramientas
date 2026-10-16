package com.rentatools.shared.infrastructure.storage;

import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.application.port.out.StoredFile;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link FileStorage} sobre el sistema de archivos local.
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class LocalFileStorage implements FileStorage {

    private final Path rootLocation;

    public LocalFileStorage(StorageProperties properties) {
        this.rootLocation = Path.of(properties.location()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage directory: " + this.rootLocation, e);
        }
    }

    @Override
    public StoredFile store(String folder, String originalFilename, String contentType, InputStream content, long size) {
        Objects.requireNonNull(folder, "folder must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(content, "content must not be null");

        String cleanFolder = folder.replace('\\', '/').replaceAll("^/+|/+$", "");
        String extension = extractExtension(originalFilename, contentType);
        String storageKey = cleanFolder + "/" + Ids.newId() + extension;

        Path destination = resolveSafe(storageKey);
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
            long actualSize = Files.size(destination);
            return new StoredFile(storageKey, contentType, actualSize);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + storageKey, e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path file = resolveSafe(storageKey);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new NotFoundException("FILE_NOT_FOUND", "File not found: " + storageKey);
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path file = resolveSafe(storageKey);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete file: " + storageKey, e);
        }
    }

    private Path resolveSafe(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Path resolved = this.rootLocation.resolve(storageKey).normalize();
        if (!resolved.startsWith(this.rootLocation)) {
            throw new BusinessRuleViolation("INVALID_STORAGE_KEY", "Invalid storage path: " + storageKey);
        }
        return resolved;
    }

    private static String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot).toLowerCase();
                if (ext.matches("\\.[a-z0-9]{1,8}")) {
                    return ext;
                }
            }
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
