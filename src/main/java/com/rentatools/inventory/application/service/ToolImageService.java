package com.rentatools.inventory.application.service;

import com.rentatools.inventory.application.dto.ToolImageView;
import com.rentatools.inventory.application.dto.UploadToolImageCommand;
import com.rentatools.inventory.application.port.in.RemoveToolImageUseCase;
import com.rentatools.inventory.application.port.in.ReorderToolImagesUseCase;
import com.rentatools.inventory.application.port.in.UploadToolImageUseCase;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.domain.exception.ImageLimitExceededException;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImage;
import com.rentatools.inventory.domain.model.tool.ToolImageId;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.application.port.out.StoredFile;
import com.rentatools.shared.domain.Actor;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación que gestiona la galería de imágenes de una herramienta ({@link ToolImage}).
 */
public class ToolImageService implements
        UploadToolImageUseCase,
        RemoveToolImageUseCase,
        ReorderToolImagesUseCase {

    private final ToolRepository toolRepository;
    private final FileStorage fileStorage;
    private final Clock clock;

    public ToolImageService(ToolRepository toolRepository, FileStorage fileStorage, Clock clock) {
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository must not be null");
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ToolImageView uploadToolImage(Actor actor, UploadToolImageCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Tool tool = loadToolOrThrow(command.toolId());
        tool.assertOwnedBy(actor);

        if (tool.images().size() >= Tool.MAX_IMAGES) {
            throw new ImageLimitExceededException(
                    "Tool " + tool.id() + " already has the maximum of " + Tool.MAX_IMAGES + " images"
            );
        }

        String normalizedContentType = command.contentType().trim().toLowerCase();
        if (!ToolImage.ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new IllegalArgumentException("Unsupported image contentType: " + command.contentType());
        }
        if (command.sizeBytes() <= 0 || command.sizeBytes() > ToolImage.MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Image sizeBytes must be between 1 and " + ToolImage.MAX_SIZE_BYTES + ", got: " + command.sizeBytes()
            );
        }

        StoredFile stored = fileStorage.store(
                "tools/" + tool.id().value(),
                command.originalFilename(),
                normalizedContentType,
                command.content(),
                command.sizeBytes()
        );

        ToolImage added = tool.addImage(
                ToolImageId.newId(),
                stored.storageKey(),
                stored.contentType(),
                stored.sizeBytes(),
                clock.instant()
        );

        toolRepository.save(tool);
        return ToolImageView.from(added);
    }

    @Override
    public void removeToolImage(Actor actor, UUID toolId, UUID imageId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(imageId, "imageId must not be null");

        Tool tool = loadToolOrThrow(toolId);
        tool.assertOwnedBy(actor);

        ToolImageId targetImageId = ToolImageId.of(imageId);
        boolean exists = tool.images().stream().anyMatch(img -> img.id().equals(targetImageId));
        if (!exists) {
            throw new NotFoundException(
                    "TOOL_IMAGE_NOT_FOUND",
                    "Image " + targetImageId + " not found on tool " + tool.id()
            );
        }

        ToolImage removed = tool.removeImage(targetImageId, clock.instant());
        toolRepository.save(tool);
        fileStorage.delete(removed.storageKey());
    }

    @Override
    public List<ToolImageView> reorderToolImages(Actor actor, UUID toolId, List<UUID> orderedImageIds) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(orderedImageIds, "orderedImageIds must not be null");

        Tool tool = loadToolOrThrow(toolId);
        tool.assertOwnedBy(actor);

        List<ToolImageId> imageIds = orderedImageIds.stream()
                .map(ToolImageId::of)
                .toList();

        tool.reorderImages(imageIds, clock.instant());
        Tool saved = toolRepository.save(tool);

        return saved.images().stream()
                .map(ToolImageView::from)
                .toList();
    }

    private Tool loadToolOrThrow(UUID toolId) {
        ToolId id = ToolId.of(toolId);
        return toolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TOOL_NOT_FOUND", "Tool not found: " + id));
    }
}
