package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import com.rentatools.iam.domain.model.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de petición para cambiar el estado de un usuario.
 */
public record ChangeUserStatusRequest(
        @NotNull UserStatus status
) {
}
