package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import com.rentatools.iam.application.dto.SessionTokens;
import java.time.Instant;

/**
 * Respuesta JSON con el par de tokens de sesión (access + refresh) y los datos del usuario autenticado.
 */
public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserResponse user
) {

    public static TokenResponse from(SessionTokens tokens) {
        return new TokenResponse(
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt(),
                UserResponse.from(tokens.user())
        );
    }
}
