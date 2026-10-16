package com.rentatools.billing.domain.model.invoice;

import java.util.Objects;

/**
 * Concepto de línea facturable por unidad de reserva.
 * Por regla de dominio (DDL-4FN §8 regla 5):
 * <ul>
 *     <li>{@link #RENTAL} va únicamente en facturas {@link InvoiceType#RENTAL}.</li>
 *     <li>{@link #LATE_FEE} y {@link #DAMAGE} van únicamente en facturas {@link InvoiceType#SETTLEMENT}.</li>
 * </ul>
 */
public enum LineConcept {
    RENTAL,
    LATE_FEE,
    DAMAGE;

    public boolean isAllowedFor(InvoiceType invoiceType) {
        Objects.requireNonNull(invoiceType, "invoiceType must not be null");
        return switch (this) {
            case RENTAL -> invoiceType == InvoiceType.RENTAL;
            case LATE_FEE, DAMAGE -> invoiceType == InvoiceType.SETTLEMENT;
        };
    }
}
