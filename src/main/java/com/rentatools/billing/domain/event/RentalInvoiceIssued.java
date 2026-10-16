package com.rentatools.billing.domain.event;

import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio registrado cuando se emite la factura de alquiler ({@code RENTAL}) de una reserva.
 */
public record RentalInvoiceIssued(
        UUID eventId,
        Instant occurredAt,
        InvoiceId invoiceId,
        UUID reservationId,
        InvoiceNumber number,
        Money total
) implements DomainEvent {

    public RentalInvoiceIssued {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(total, "total must not be null");
    }

    public static RentalInvoiceIssued of(
            InvoiceId invoiceId,
            UUID reservationId,
            InvoiceNumber number,
            Money total,
            Instant occurredAt
    ) {
        return new RentalInvoiceIssued(Ids.newId(), occurredAt, invoiceId, reservationId, number, total);
    }
}
