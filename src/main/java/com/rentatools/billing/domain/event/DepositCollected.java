package com.rentatools.billing.domain.event;

import com.rentatools.billing.domain.model.deposit.DepositId;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio registrado cuando se recauda un importe en el depósito de garantía de una reserva.
 */
public record DepositCollected(
        UUID eventId,
        Instant occurredAt,
        DepositId depositId,
        UUID reservationId,
        Money collectedAmount,
        Money currentBalance,
        Money requiredAmount
) implements DomainEvent {

    public DepositCollected {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(depositId, "depositId must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(collectedAmount, "collectedAmount must not be null");
        Objects.requireNonNull(currentBalance, "currentBalance must not be null");
        Objects.requireNonNull(requiredAmount, "requiredAmount must not be null");
    }

    public static DepositCollected of(
            DepositId depositId,
            UUID reservationId,
            Money collectedAmount,
            Money currentBalance,
            Money requiredAmount,
            Instant occurredAt
    ) {
        return new DepositCollected(
                Ids.newId(),
                occurredAt,
                depositId,
                reservationId,
                collectedAmount,
                currentBalance,
                requiredAmount
        );
    }
}
