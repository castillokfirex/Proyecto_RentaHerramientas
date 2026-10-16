package com.rentatools.iam.application.port.out;

import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de salida de persistencia para el agregado {@link RefreshToken}.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeAllActiveByUserId(UserId userId, Instant revokedAt);
}
