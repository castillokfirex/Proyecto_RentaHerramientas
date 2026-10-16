package com.rentatools.iam.application.port.out;

import java.time.Duration;
import java.util.Objects;

/**
 * Puerto de salida para generar tokens opacos criptográficamente seguros y calcular su hash SHA-256.
 */
public interface SecureTokenGenerator {

    GeneratedRefreshToken generate();

    String hash(String rawToken);

    default Duration refreshTokenTtl() {
        return Duration.ofDays(7);
    }

    record GeneratedRefreshToken(String rawToken, String tokenHash) {
        public GeneratedRefreshToken {
            Objects.requireNonNull(rawToken, "rawToken must not be null");
            Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        }
    }
}
