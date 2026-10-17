package com.rentatools.inventory.domain.exception;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Se lanza cuando ya existe una unidad física con el mismo número de serie dentro de la misma herramienta.
 */
public final class DuplicateSerialNumberException extends BusinessRuleViolation {

    public DuplicateSerialNumberException(String message) {
        super("DUPLICATE_SERIAL_NUMBER", message);
    }
}
