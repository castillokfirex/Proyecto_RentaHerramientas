package com.rentatools.iam.application.port.out;

import com.rentatools.iam.domain.model.User;
import java.time.Instant;
import java.util.Objects;

/**
 * Puerto de salida para emisión de access tokens JWT firmados.
 */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user, Instant issuedAt);

    record IssuedAccessToken(String token, Instant expiresAt) {
        public IssuedAccessToken {
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
