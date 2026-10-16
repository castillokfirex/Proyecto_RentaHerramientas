package com.rentatools.billing.domain.model.invoice;

/**
 * Naturaleza de un movimiento de pago en una factura (los importes son siempre positivos).
 */
public enum PaymentKind {
    PAYMENT,
    REFUND
}
