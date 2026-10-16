package com.rentatools.shared.application.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando el actor autenticado no tiene permisos para realizar la operación (HTTP 403).
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
