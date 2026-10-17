package com.rentatools.rental.domain.model.inspection;

import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.Objects;

/**
 * Resultado de la evaluación de un ítem del checklist dentro de una inspección (`inspection_results`).
 */
public record ChecklistResult(
        ChecklistItemId checklistItemId,
        ResultValue result,
        String note
) {

    private static final int MAX_NOTE_LENGTH = 300;

    public ChecklistResult {
        Objects.requireNonNull(checklistItemId, "checklistItemId must not be null");
        Objects.requireNonNull(result, "result must not be null");
        if (note != null) {
            String trimmed = note.trim();
            if (trimmed.isEmpty()) {
                note = null;
            } else if (trimmed.length() > MAX_NOTE_LENGTH) {
                throw new BusinessRuleViolation(
                        "CHECKLIST_NOTE_TOO_LONG",
                        "Checklist result note cannot exceed " + MAX_NOTE_LENGTH + " characters"
                );
            } else {
                note = trimmed;
            }
        }
    }

    public static ChecklistResult of(ChecklistItemId checklistItemId, ResultValue result, String note) {
        return new ChecklistResult(checklistItemId, result, note);
    }

    public static ChecklistResult of(ChecklistItemId checklistItemId, ResultValue result) {
        return new ChecklistResult(checklistItemId, result, null);
    }

    public boolean isDamagedOrMissing() {
        return result.isAdverse();
    }
}
