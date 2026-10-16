package com.rentatools.billing.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando un pago o retención provocaría que el monto abonado supere el total exigible.
 */
public final class OverpaymentException extends DomainException {

    public static final String CODE = "OVERPAYMENT";

    public OverpaymentException(String message) {
        super(CODE, message);
    }
}
