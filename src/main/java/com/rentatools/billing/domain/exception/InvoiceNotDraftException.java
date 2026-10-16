package com.rentatools.billing.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Se lanza cuando se intenta modificar las líneas o emitir una factura que ya no está en borrador.
 */
public final class InvoiceNotDraftException extends DomainException {

    public static final String CODE = "INVOICE_NOT_DRAFT";

    public InvoiceNotDraftException(String message) {
        super(CODE, message);
    }
}
