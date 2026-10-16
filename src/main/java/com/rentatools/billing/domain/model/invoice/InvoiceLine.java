package com.rentatools.billing.domain.model.invoice;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad interna del agregado {@link Invoice} que representa una línea facturada por unidad y concepto.
 * En BD: {@code invoice_lines} con restricción única {@code (reservation_unit_id, concept)}.
 */
public record InvoiceLine(
        UUID id,
        UUID reservationUnitId,
        LineConcept concept,
        String description,
        BigDecimal quantity,
        Money unitPrice
) {

    public InvoiceLine {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        Objects.requireNonNull(concept, "concept must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");

        String trimmed = description.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_LINE_DESCRIPTION",
                    "Invoice line description must be between 1 and 200 characters"
            );
        }
        description = trimmed;

        quantity = quantity.setScale(2, RoundingMode.HALF_UP);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_LINE_QUANTITY",
                    "Invoice line quantity must be greater than zero, got: " + quantity
            );
        }

        if (unitPrice.isNegative()) {
            throw new BusinessRuleViolation(
                    "INVALID_INVOICE_LINE_UNIT_PRICE",
                    "Invoice line unit price must not be negative, got: " + unitPrice
            );
        }
    }

    public static InvoiceLine create(
            UUID reservationUnitId,
            LineConcept concept,
            String description,
            BigDecimal quantity,
            Money unitPrice
    ) {
        return new InvoiceLine(Ids.newId(), reservationUnitId, concept, description, quantity, unitPrice);
    }

    public static InvoiceLine create(
            UUID reservationUnitId,
            LineConcept concept,
            String description,
            long quantity,
            Money unitPrice
    ) {
        return create(reservationUnitId, concept, description, BigDecimal.valueOf(quantity), unitPrice);
    }

    /**
     * Importe derivado de la línea: {@code quantity × unitPrice} con redondeo HALF_UP a 2 decimales.
     */
    public Money lineTotal() {
        return unitPrice.times(quantity);
    }
}
