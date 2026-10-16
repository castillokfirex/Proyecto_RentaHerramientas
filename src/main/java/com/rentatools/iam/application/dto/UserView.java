package com.rentatools.iam.application.dto;

import com.rentatools.iam.domain.model.ProviderProfile;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.domain.ActorRole;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Proyección de lectura de un usuario en la capa de aplicación.
 */
public record UserView(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        Set<ActorRole> roles,
        ProviderProfileView providerProfile,
        Instant createdAt
) {

    public UserView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        roles = Set.copyOf(roles);
    }

    public static UserView from(User user) {
        Objects.requireNonNull(user, "user must not be null");
        ProviderProfile profile = user.providerProfile();
        ProviderProfileView profileView = profile == null
                ? null
                : new ProviderProfileView(
                        profile.businessName(),
                        profile.taxId().value(),
                        profile.isApproved(),
                        profile.approvedAt(),
                        profile.approvedBy() != null ? profile.approvedBy().value() : null
                );

        return new UserView(
                user.id().value(),
                user.email().value(),
                user.fullName(),
                user.phone(),
                user.status(),
                user.roles(),
                profileView,
                user.createdAt()
        );
    }

    public record ProviderProfileView(
            String businessName,
            String taxId,
            boolean approved,
            Instant approvedAt,
            UUID approvedBy
    ) {
    }
}
