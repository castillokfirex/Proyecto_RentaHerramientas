package com.rentatools.rental.application.dto;

import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista detallada de una inspección con sus resultados de checklist y fotos asociadas.
 */
public record InspectionView(
        UUID id,
        UUID reservationUnitId,
        InspectionType type,
        UUID inspectorId,
        Instant performedAt,
        OverallCondition overallCondition,
        String observations,
        List<ChecklistResultView> results,
        List<InspectionPhotoView> photos
) {
    public InspectionView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(inspectorId, "inspectorId must not be null");
        Objects.requireNonNull(performedAt, "performedAt must not be null");
        Objects.requireNonNull(overallCondition, "overallCondition must not be null");
        results = results != null ? List.copyOf(results) : List.of();
        photos = photos != null ? List.copyOf(photos) : List.of();
    }
}
