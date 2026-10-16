package com.rentatools.billing.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta registrar más de una línea para el mismo par (reservation_unit_id, concept).
 */
public final class DuplicateChargeException extends DomainException {

    public static final String CODE = "DUPLICATE_CHARGE";

    public DuplicateChargeException(String message) {
        super(CODE, message);
    }
}
