package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de petición para rotación o revocación de un refresh token.
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
