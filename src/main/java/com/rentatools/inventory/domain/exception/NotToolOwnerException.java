package com.rentatools.inventory.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando un actor intenta modificar una herramienta que no pertenece a su cuenta de proveedor.
 */
public final class NotToolOwnerException extends DomainException {

    public NotToolOwnerException(String message) {
        super("NOT_TOOL_OWNER", message);
    }
}
