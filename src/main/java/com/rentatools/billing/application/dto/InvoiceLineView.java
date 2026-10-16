package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.invoice.InvoiceLine;
import com.rentatools.billing.domain.model.invoice.LineConcept;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO de lectura para una línea de factura.
 */
public record InvoiceLineView(
        UUID id,
        UUID reservationUnitId,
        LineConcept concept,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static InvoiceLineView from(InvoiceLine line) {
        Objects.requireNonNull(line, "line must not be null");
        return new InvoiceLineView(
                line.id(),
                line.reservationUnitId(),
                line.concept(),
                line.description(),
                line.quantity(),
                line.unitPrice().amount(),
                line.lineTotal().amount()
        );
    }
}
