package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.InspectionPhotoView;
import java.time.Instant;
import java.util.UUID;

public record InspectionPhotoResponse(
        UUID id,
        UUID inspectionId,
        String storageKey,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public static InspectionPhotoResponse from(InspectionPhotoView view) {
        return new InspectionPhotoResponse(
                view.id(),
                view.inspectionId(),
                view.storageKey(),
                view.contentType(),
                view.sizeBytes(),
                view.uploadedAt()
        );
    }
}
