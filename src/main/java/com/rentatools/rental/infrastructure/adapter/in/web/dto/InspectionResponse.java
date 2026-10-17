package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.ChecklistResultView;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(
        UUID id,
        UUID reservationUnitId,
        String type,
        UUID inspectorId,
        Instant performedAt,
        String overallCondition,
        String observations,
        List<ChecklistResultResponse> results,
        List<InspectionPhotoResponse> photos
) {
    public static InspectionResponse from(InspectionView view) {
        List<ChecklistResultResponse> resultResponses = view.results().stream()
                .map(ChecklistResultResponse::from)
                .toList();

        List<InspectionPhotoResponse> photoResponses = view.photos().stream()
                .map(InspectionPhotoResponse::from)
                .toList();

        return new InspectionResponse(
                view.id(),
                view.reservationUnitId(),
                view.type().name(),
                view.inspectorId(),
                view.performedAt(),
                view.overallCondition().name(),
                view.observations(),
                resultResponses,
                photoResponses
        );
    }

    public record ChecklistResultResponse(
            int checklistItemId,
            String checklistItemCode,
            String checklistItemLabel,
            String result,
            String note
    ) {
        public static ChecklistResultResponse from(ChecklistResultView v) {
            return new ChecklistResultResponse(
                    v.checklistItemId(),
                    v.checklistItemCode(),
                    v.checklistItemLabel(),
                    v.result().name(),
                    v.note()
            );
        }
    }
}
