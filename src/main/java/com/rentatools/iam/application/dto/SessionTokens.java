package com.rentatools.iam.application.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Par de tokens emitidos al iniciar o refrescar una sesión junto con la vista del usuario autenticado.
 */
public record SessionTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserView user
) {

    public SessionTokens {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt must not be null");
        Objects.requireNonNull(user, "user must not be null");
    }
}
