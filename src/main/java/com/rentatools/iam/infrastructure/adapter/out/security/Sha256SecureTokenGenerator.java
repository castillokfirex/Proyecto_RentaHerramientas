package com.rentatools.iam.infrastructure.adapter.out.security;

import com.rentatools.iam.application.port.out.SecureTokenGenerator;
import com.rentatools.shared.infrastructure.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida que genera refresh tokens opacos seguros (256 bits de entropía)
 * y calcula su resumen SHA-256 en hexadecimal (64 caracteres) para persistencia.
 */
@Component
public class Sha256SecureTokenGenerator implements SecureTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtProperties jwtProperties;

    public Sha256SecureTokenGenerator(JwtProperties jwtProperties) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
    }

    @Override
    public GeneratedRefreshToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new GeneratedRefreshToken(rawToken, hash(rawToken));
    }

    @Override
    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public Duration refreshTokenTtl() {
        return jwtProperties.refreshTokenTtl();
    }
}
