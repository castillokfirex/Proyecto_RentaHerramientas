package com.rentatools.iam.application.service;

import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.port.in.GetCurrentUserQuery;
import com.rentatools.iam.application.port.in.ListUsersQuery;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.ActorRole;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación para consultas de lectura sobre usuarios.
 */
public class UserQueryService implements GetCurrentUserQuery, ListUsersQuery {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    @Override
    public UserView getById(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        User user = userRepository.findById(UserId.of(userId))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userId));
        return UserView.from(user);
    }

    @Override
    public PageResult<UserView> listUsers(PageQuery pageQuery, ActorRole roleFilter, UserStatus statusFilter) {
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
        return userRepository.findAll(pageQuery, roleFilter, statusFilter)
                .map(UserView::from);
    }
}
