package com.rentatools.billing.domain.model.invoice;

/**
 * Estado derivado de una factura (no se almacena como columna en BD por 3FN/FNBC).
 * <ul>
 *     <li>{@link #DRAFT}: {@code issuedAt == null}</li>
 *     <li>{@link #ISSUED}: {@code issuedAt != null && voidedAt == null}</li>
 *     <li>{@link #VOIDED}: {@code voidedAt != null}</li>
 * </ul>
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    VOIDED
}
