package com.rentatools.inventory.domain.exception;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Se lanza cuando la asignación de {@code parentId} en una categoría introduce un ciclo en el árbol de categorías.
 */
public final class CategoryCycleException extends BusinessRuleViolation {

    public CategoryCycleException(String message) {
        super("CATEGORY_CYCLE", message);
    }
}
