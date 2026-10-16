package com.rentatools.iam.domain.event;

import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando se registra un nuevo usuario (cliente o proveedor).
 */
public record UserRegistered(
        UUID eventId,
        Instant occurredAt,
        UserId userId,
        Email email,
        Set<ActorRole> roles
) implements DomainEvent {

    public UserRegistered {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        roles = Set.copyOf(roles);
    }

    public static UserRegistered of(UserId userId, Email email, Set<ActorRole> roles, Instant occurredAt) {
        return new UserRegistered(Ids.newId(occurredAt), occurredAt, userId, email, roles);
    }
}
