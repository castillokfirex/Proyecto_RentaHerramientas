package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.RegisterInspectionCommand;
import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.inspection.ResultValue;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RegisterInspectionRequest(
        @NotBlank String type,
        @NotBlank String overallCondition,
        String observations,
        List<ChecklistResultRequest> results
) {
    public RegisterInspectionCommand toCommand(UUID reservationId, UUID reservationUnitId) {
        List<RegisterInspectionCommand.ChecklistResultItem> items = new ArrayList<>();
        if (results != null) {
            for (ChecklistResultRequest r : results) {
                items.add(new RegisterInspectionCommand.ChecklistResultItem(
                        r.checklistItemId(),
                        ResultValue.valueOf(r.result().trim().toUpperCase()),
                        r.note()
                ));
            }
        }
        return new RegisterInspectionCommand(
                reservationId,
                reservationUnitId,
                InspectionType.valueOf(type.trim().toUpperCase()),
                OverallCondition.valueOf(overallCondition.trim().toUpperCase()),
                observations,
                items
        );
    }

    public record ChecklistResultRequest(
            int checklistItemId,
            @NotBlank String result,
            String note
    ) {
    }
}
