package com.rentatools.billing.domain.model.invoice;

/**
 * Medio de pago o devolución admitido en facturas y garantías.
 */
public enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    SIMULATED
}
