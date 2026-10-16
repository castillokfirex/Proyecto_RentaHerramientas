package com.rentatools.iam.api;

import com.rentatools.shared.domain.ActorRole;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Vista resumida de un usuario expuesta por el contexto IAM a otros módulos.
 */
public record UserSummary(
        UUID id,
        String fullName,
        String email,
        Set<ActorRole> roles,
        boolean active
) {

    public UserSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        roles = Set.copyOf(roles);
    }
}
