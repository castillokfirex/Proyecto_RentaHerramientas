package com.rentatools.billing.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta registrar movimientos o retenciones sobre una garantía ya liquidada.
 */
public final class DepositAlreadySettledException extends DomainException {

    public static final String CODE = "DEPOSIT_ALREADY_SETTLED";

    public DepositAlreadySettledException(String message) {
        super(CODE, message);
    }
}
