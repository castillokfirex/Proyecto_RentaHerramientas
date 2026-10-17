package com.rentatools.inventory.infrastructure.adapter.in.web;

import com.rentatools.inventory.application.dto.UploadToolImageCommand;
import com.rentatools.inventory.application.port.in.RemoveToolImageUseCase;
import com.rentatools.inventory.application.port.in.ReorderToolImagesUseCase;
import com.rentatools.inventory.application.port.in.UploadToolImageUseCase;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.ReorderToolImagesRequest;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.ToolImageResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controlador REST para la gestión de imágenes de herramientas (subida multipart con {@code FileStorage},
 * reordenamiento y borrado) por parte del proveedor dueño.
 */
@RestController
@RequestMapping({"/api/v1/provider/tools/{toolId}/images", "/api/v1/tools/{toolId}/images"})
@PreAuthorize("hasRole('PROVEEDOR')")
public class ProviderToolImageController {

    private final UploadToolImageUseCase uploadToolImageUseCase;
    private final RemoveToolImageUseCase removeToolImageUseCase;
    private final ReorderToolImagesUseCase reorderToolImagesUseCase;

    public ProviderToolImageController(
            UploadToolImageUseCase uploadToolImageUseCase,
            RemoveToolImageUseCase removeToolImageUseCase,
            ReorderToolImagesUseCase reorderToolImagesUseCase
    ) {
        this.uploadToolImageUseCase = Objects.requireNonNull(uploadToolImageUseCase, "uploadToolImageUseCase must not be null");
        this.removeToolImageUseCase = Objects.requireNonNull(removeToolImageUseCase, "removeToolImageUseCase must not be null");
        this.reorderToolImagesUseCase = Objects.requireNonNull(reorderToolImagesUseCase, "reorderToolImagesUseCase must not be null");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ToolImageResponse uploadImage(
            @CurrentActor Actor actor,
            @PathVariable UUID toolId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded image file must not be empty");
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";

        try (InputStream in = file.getInputStream()) {
            return ToolImageResponse.from(uploadToolImageUseCase.uploadToolImage(
                    actor,
                    new UploadToolImageCommand(toolId, filename, contentType, file.getSize(), in)
            ));
        }
    }

    @PutMapping({"/order", ""})
    public List<ToolImageResponse> reorderImages(
            @CurrentActor Actor actor,
            @PathVariable UUID toolId,
            @Valid @RequestBody ReorderToolImagesRequest request
    ) {
        return reorderToolImagesUseCase.reorderToolImages(actor, toolId, request.orderedImageIds())
                .stream()
                .map(ToolImageResponse::from)
                .toList();
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @CurrentActor Actor actor,
            @PathVariable UUID toolId,
            @PathVariable UUID imageId
    ) {
        removeToolImageUseCase.removeToolImage(actor, toolId, imageId);
        return ResponseEntity.noContent().build();
    }
}
