package com.rentatools.inventory.domain.exception;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Se lanza cuando una herramienta no cumple las condiciones requeridas para pasar a estado {@code ACTIVE}:
 * tarifa {@code DAY} configurada, al menos una unidad física {@code AVAILABLE} y proveedor aprobado.
 */
public final class ToolNotPublishableException extends BusinessRuleViolation {

    public ToolNotPublishableException(String message) {
        super("TOOL_NOT_PUBLISHABLE", message);
    }
}
