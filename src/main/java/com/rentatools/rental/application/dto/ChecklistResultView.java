package com.rentatools.rental.application.dto;

import com.rentatools.rental.domain.model.inspection.ResultValue;
import java.util.Objects;

/**
 * Vista de un resultado individual de un ítem de checklist en una inspección.
 */
public record ChecklistResultView(
        int checklistItemId,
        String checklistItemCode,
        String checklistItemLabel,
        ResultValue result,
        String note
) {
    public ChecklistResultView {
        Objects.requireNonNull(result, "result must not be null");
    }
}
