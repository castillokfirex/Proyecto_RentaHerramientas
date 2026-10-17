package com.rentatools.rental.infrastructure.adapter.in.web;

import com.rentatools.rental.application.dto.AttachInspectionPhotoCommand;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.application.port.in.AttachInspectionPhotoUseCase;
import com.rentatools.rental.application.port.in.GetInspectionQuery;
import com.rentatools.rental.application.port.in.RegisterInspectionUseCase;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.InspectionPhotoResponse;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.InspectionResponse;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.RegisterInspectionRequest;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controlador REST para el registro y consulta de inspecciones y subida de fotografías de evidencia.
 */
@RestController
public class InspectionController {

    private final RegisterInspectionUseCase registerInspectionUseCase;
    private final AttachInspectionPhotoUseCase attachInspectionPhotoUseCase;
    private final GetInspectionQuery getInspectionQuery;

    public InspectionController(
            RegisterInspectionUseCase registerInspectionUseCase,
            AttachInspectionPhotoUseCase attachInspectionPhotoUseCase,
            GetInspectionQuery getInspectionQuery
    ) {
        this.registerInspectionUseCase = Objects.requireNonNull(registerInspectionUseCase, "registerInspectionUseCase must not be null");
        this.attachInspectionPhotoUseCase = Objects.requireNonNull(attachInspectionPhotoUseCase, "attachInspectionPhotoUseCase must not be null");
        this.getInspectionQuery = Objects.requireNonNull(getInspectionQuery, "getInspectionQuery must not be null");
    }

    @PostMapping("/api/v1/reservations/{reservationId}/units/{unitId}/inspections")
    @PreAuthorize("hasAnyRole('PROVEEDOR', 'ADMIN')")
    public ResponseEntity<InspectionResponse> registerInspection(
            @CurrentActor Actor actor,
            @PathVariable UUID reservationId,
            @PathVariable UUID unitId,
            @Valid @RequestBody RegisterInspectionRequest request
    ) {
        InspectionView view = registerInspectionUseCase.registerInspection(actor, request.toCommand(reservationId, unitId));
        return ResponseEntity
                .created(URI.create("/api/v1/inspections/" + view.id()))
                .body(InspectionResponse.from(view));
    }

    @GetMapping("/api/v1/inspections/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public InspectionResponse getInspection(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return InspectionResponse.from(getInspectionQuery.getInspection(actor, id));
    }

    @GetMapping("/api/v1/reservations/{reservationId}/inspections")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public List<InspectionResponse> listInspectionsByReservation(
            @CurrentActor Actor actor,
            @PathVariable UUID reservationId
    ) {
        return getInspectionQuery.listInspectionsByReservation(actor, reservationId).stream()
                .map(InspectionResponse::from)
                .toList();
    }

    @PostMapping(value = "/api/v1/inspections/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PROVEEDOR', 'ADMIN')")
    public ResponseEntity<InspectionPhotoResponse> attachPhoto(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded photo file must not be empty");
        }

        try (InputStream in = file.getInputStream()) {
            AttachInspectionPhotoCommand command = new AttachInspectionPhotoCommand(
                    id,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    in
            );
            InspectionPhotoView view = attachInspectionPhotoUseCase.attachPhoto(actor, command);
            return ResponseEntity
                    .created(URI.create("/api/v1/inspections/" + id + "/photos/" + view.id()))
                    .body(InspectionPhotoResponse.from(view));
        }
    }

    @GetMapping("/api/v1/inspections/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public ResponseEntity<byte[]> getPhoto(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @PathVariable UUID photoId
    ) throws IOException {
        GetInspectionQuery.InspectionPhotoResource res = getInspectionQuery.getInspectionPhotoResource(actor, id, photoId);
        try (InputStream in = res.stream()) {
            byte[] bytes = in.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(res.view().contentType()))
                    .body(bytes);
        }
    }
}
