package com.rentatools.iam.application.service;

import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.port.in.ApproveProviderUseCase;
import com.rentatools.iam.application.port.in.ChangeUserStatusUseCase;
import com.rentatools.iam.application.port.out.RefreshTokenRepository;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación para operaciones administrativas sobre cuentas de usuario y proveedores.
 */
public class UserAdministrationService implements ApproveProviderUseCase, ChangeUserStatusUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public UserAdministrationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public UserView approveProvider(Actor actor, UUID providerUserId) {
        assertAdmin(actor);
        Objects.requireNonNull(providerUserId, "providerUserId must not be null");

        UserId targetId = UserId.of(providerUserId);
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + providerUserId));

        Instant now = clock.instant();
        user.approveProvider(UserId.of(actor.userId()), now);

        User saved = userRepository.save(user);
        return UserView.from(saved);
    }

    @Override
    public UserView changeStatus(Actor actor, UUID targetUserId, UserStatus newStatus) {
        assertAdmin(actor);
        Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        if (actor.userId().equals(targetUserId) && newStatus == UserStatus.SUSPENDED) {
            throw new BusinessRuleViolation("CANNOT_SUSPEND_SELF", "An administrator cannot suspend their own account");
        }

        UserId targetId = UserId.of(targetUserId);
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + targetUserId));

        Instant now = clock.instant();
        user.changeStatus(newStatus, now);
        User saved = userRepository.save(user);

        if (newStatus == UserStatus.SUSPENDED) {
            refreshTokenRepository.revokeAllActiveByUserId(targetId, now);
        }

        return UserView.from(saved);
    }

    private static void assertAdmin(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.is(ActorRole.ADMIN)) {
            throw new ForbiddenException("ADMIN_REQUIRED", "Only an ADMIN can perform this operation");
        }
    }
}
