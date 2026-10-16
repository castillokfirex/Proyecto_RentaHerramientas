package com.rentatools.billing.domain.model.invoice;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Número secuencial positivo asignado al emitir una factura.
 */
public record InvoiceNumber(long value) implements Comparable<InvoiceNumber> {

    public InvoiceNumber {
        if (value <= 0) {
            throw new BusinessRuleViolation("INVALID_INVOICE_NUMBER", "Invoice number must be positive, got: " + value);
        }
    }

    public static InvoiceNumber of(long value) {
        return new InvoiceNumber(value);
    }

    @Override
    public int compareTo(InvoiceNumber other) {
        return Long.compare(this.value, other.value);
    }
}
