package com.rentatools.iam.domain.event;

import com.rentatools.iam.domain.model.UserId;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando un administrador aprueba el perfil de un proveedor.
 */
public record ProviderApproved(
        UUID eventId,
        Instant occurredAt,
        UserId providerId,
        UserId approvedBy
) implements DomainEvent {

    public ProviderApproved {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
    }

    public static ProviderApproved of(UserId providerId, UserId approvedBy, Instant occurredAt) {
        return new ProviderApproved(Ids.newId(occurredAt), occurredAt, providerId, approvedBy);
    }
}
