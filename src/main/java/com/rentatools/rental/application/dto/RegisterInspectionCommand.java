package com.rentatools.rental.application.dto;

import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.inspection.ResultValue;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para registrar una inspección de entrega o devolución de una unidad reservada.
 */
public record RegisterInspectionCommand(
        UUID reservationId,
        UUID reservationUnitId,
        InspectionType type,
        OverallCondition overallCondition,
        String observations,
        List<ChecklistResultItem> results
) {
    public RegisterInspectionCommand {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(overallCondition, "overallCondition must not be null");
        results = results != null ? List.copyOf(results) : List.of();
    }

    public record ChecklistResultItem(
            int checklistItemId,
            ResultValue result,
            String note
    ) {
        public ChecklistResultItem {
            Objects.requireNonNull(result, "result must not be null");
        }
    }
}
