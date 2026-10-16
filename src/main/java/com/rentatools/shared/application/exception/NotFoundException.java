package com.rentatools.shared.application.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando no se encuentra un recurso solicitado (HTTP 404).
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
