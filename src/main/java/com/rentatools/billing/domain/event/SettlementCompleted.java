package com.rentatools.billing.domain.event;

import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio registrado cuando concluye la liquidación de la garantía de una reserva.
 * Nota: {@code settlementInvoiceId} puede ser {@code null} cuando no hubo cargos adicionales
 * y el borrador de liquidación se descartó devolviendo la totalidad de la garantía.
 */
public record SettlementCompleted(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        InvoiceId settlementInvoiceId,
        Money retainedFromDeposit,
        Money refundedFromDeposit,
        Money extraChargesTotal,
        Money remainingBalance
) implements DomainEvent {

    public SettlementCompleted {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(retainedFromDeposit, "retainedFromDeposit must not be null");
        Objects.requireNonNull(refundedFromDeposit, "refundedFromDeposit must not be null");
        Objects.requireNonNull(extraChargesTotal, "extraChargesTotal must not be null");
        Objects.requireNonNull(remainingBalance, "remainingBalance must not be null");
    }

    public static SettlementCompleted of(
            UUID reservationId,
            InvoiceId settlementInvoiceId,
            Money retainedFromDeposit,
            Money refundedFromDeposit,
            Money extraChargesTotal,
            Money remainingBalance,
            Instant occurredAt
    ) {
        return new SettlementCompleted(
                Ids.newId(),
                occurredAt,
                reservationId,
                settlementInvoiceId,
                retainedFromDeposit,
                refundedFromDeposit,
                extraChargesTotal,
                remainingBalance
        );
    }
}
