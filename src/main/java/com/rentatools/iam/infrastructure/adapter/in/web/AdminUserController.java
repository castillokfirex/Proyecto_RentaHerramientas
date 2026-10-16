package com.rentatools.iam.infrastructure.adapter.in.web;

import com.rentatools.iam.application.port.in.ApproveProviderUseCase;
import com.rentatools.iam.application.port.in.ChangeUserStatusUseCase;
import com.rentatools.iam.application.port.in.ListUsersQuery;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.ChangeUserStatusRequest;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.UserResponse;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para administración de usuarios y aprobación de proveedores (rol {@code ADMIN}).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final ListUsersQuery listUsersQuery;
    private final ChangeUserStatusUseCase changeUserStatusUseCase;
    private final ApproveProviderUseCase approveProviderUseCase;

    public AdminUserController(
            ListUsersQuery listUsersQuery,
            ChangeUserStatusUseCase changeUserStatusUseCase,
            ApproveProviderUseCase approveProviderUseCase
    ) {
        this.listUsersQuery = listUsersQuery;
        this.changeUserStatusUseCase = changeUserStatusUseCase;
        this.approveProviderUseCase = approveProviderUseCase;
    }

    @GetMapping("/users")
    public PageResult<UserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ActorRole role,
            @RequestParam(required = false) UserStatus status
    ) {
        return listUsersQuery.listUsers(PageQuery.of(page, size), role, status)
                .map(UserResponse::from);
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse changeStatus(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequest request
    ) {
        return UserResponse.from(changeUserStatusUseCase.changeStatus(actor, id, request.status()));
    }

    @PostMapping("/providers/{id}/approve")
    public UserResponse approveProvider(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return UserResponse.from(approveProviderUseCase.approveProvider(actor, id));
    }
}
