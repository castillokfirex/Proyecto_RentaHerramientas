package com.rentatools.rental.domain.model.inspection;

/**
 * Valor de evaluación de un ítem del checklist dentro de una inspección (`inspection_results.result`).
 */
public enum ResultValue {
    OK,
    DAMAGED,
    MISSING,
    NOT_APPLICABLE;

    public boolean isAdverse() {
        return this == DAMAGED || this == MISSING;
    }
}
