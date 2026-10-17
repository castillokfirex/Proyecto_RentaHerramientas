package com.rentatools.rental.domain.model.checklist;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Identificador de un ítem del catálogo de checklist (`SMALLINT UNSIGNED` en `checklist_items.id`).
 */
public record ChecklistItemId(int value) {

    public ChecklistItemId {
        if (value <= 0 || value > 65535) {
            throw new BusinessRuleViolation(
                    "INVALID_CHECKLIST_ITEM_ID",
                    "ChecklistItemId must be between 1 and 65535"
            );
        }
    }

    public static ChecklistItemId of(int value) {
        return new ChecklistItemId(value);
    }
}
