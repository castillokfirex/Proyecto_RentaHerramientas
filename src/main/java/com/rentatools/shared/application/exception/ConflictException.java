package com.rentatools.shared.application.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada ante un conflicto de estado o unicidad (HTTP 409).
 */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}
