package com.rentatools.shared.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Representa al usuario autenticado que ejecuta una acción en el sistema.
 */
public record Actor(UUID userId, Set<ActorRole> roles) {

    public Actor {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        roles = Set.copyOf(roles);
    }

    public boolean is(ActorRole role) {
        return roles.contains(role);
    }
}
