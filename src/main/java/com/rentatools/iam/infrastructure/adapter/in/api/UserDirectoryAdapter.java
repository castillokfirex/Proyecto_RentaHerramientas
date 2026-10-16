package com.rentatools.iam.infrastructure.adapter.in.api;

import com.rentatools.iam.api.UserDirectory;
import com.rentatools.iam.api.UserSummary;
import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.port.in.GetCurrentUserQuery;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.ActorRole;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador del contrato público {@link UserDirectory} que delega en los casos de uso de IAM.
 * Sustituye a {@code JdbcUserDirectory} provisional de A0b.
 */
@Component
public class UserDirectoryAdapter implements UserDirectory {

    private final GetCurrentUserQuery getCurrentUserQuery;

    public UserDirectoryAdapter(GetCurrentUserQuery getCurrentUserQuery) {
        this.getCurrentUserQuery = Objects.requireNonNull(getCurrentUserQuery, "getCurrentUserQuery must not be null");
    }

    @Override
    public Optional<UserSummary> findById(UUID userId) {
        return findView(userId).map(view -> new UserSummary(
                view.id(),
                view.fullName(),
                view.email(),
                view.roles(),
                view.status() == UserStatus.ACTIVE
        ));
    }

    @Override
    public boolean isActiveClient(UUID userId) {
        return findView(userId)
                .map(view -> view.status() == UserStatus.ACTIVE && view.roles().contains(ActorRole.CLIENTE))
                .orElse(false);
    }

    @Override
    public boolean isApprovedProvider(UUID userId) {
        return findView(userId)
                .map(view -> view.status() == UserStatus.ACTIVE
                        && view.roles().contains(ActorRole.PROVEEDOR)
                        && view.providerProfile() != null
                        && view.providerProfile().approved())
                .orElse(false);
    }

    private Optional<UserView> findView(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        try {
            return Optional.of(getCurrentUserQuery.getById(userId));
        } catch (NotFoundException ex) {
            return Optional.empty();
        }
    }
}
