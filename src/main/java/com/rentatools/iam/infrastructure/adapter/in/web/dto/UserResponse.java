package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.domain.ActorRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Respuesta JSON que representa los datos de un usuario.
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        Set<ActorRole> roles,
        ProviderProfileResponse providerProfile,
        Instant createdAt
) {

    public static UserResponse from(UserView view) {
        UserView.ProviderProfileView p = view.providerProfile();
        ProviderProfileResponse profileResponse = p == null
                ? null
                : new ProviderProfileResponse(
                        p.businessName(),
                        p.taxId(),
                        p.approved(),
                        p.approvedAt(),
                        p.approvedBy()
                );

        return new UserResponse(
                view.id(),
                view.email(),
                view.fullName(),
                view.phone(),
                view.status(),
                view.roles(),
                profileResponse,
                view.createdAt()
        );
    }

    public record ProviderProfileResponse(
            String businessName,
            String taxId,
            boolean approved,
            Instant approvedAt,
            UUID approvedBy
    ) {
    }
}
