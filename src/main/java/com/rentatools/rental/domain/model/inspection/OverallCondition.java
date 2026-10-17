package com.rentatools.rental.domain.model.inspection;

/**
 * Dictamen global de estado físico emitido por el inspector (`overall_condition` en `inspections`).
 */
public enum OverallCondition {
    GOOD,
    MINOR_DAMAGE,
    MAJOR_DAMAGE,
    LOST;

    public boolean hasDamageReported() {
        return this == MINOR_DAMAGE || this == MAJOR_DAMAGE;
    }

    public boolean isLost() {
        return this == LOST;
    }
}
