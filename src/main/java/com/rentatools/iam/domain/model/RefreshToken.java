package com.rentatools.iam.domain.model;

import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raíz de agregado que representa un token de refresco opaco (almacenado por su hash SHA-256).
 * Soporta rotación en cada uso y revocación explícita.
 */
public final class RefreshToken extends AggregateRoot {

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;

    private RefreshToken(
            UUID id,
            UserId userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        String trimmedHash = tokenHash.trim();
        if (trimmedHash.length() != 64) {
            throw new BusinessRuleViolation("INVALID_TOKEN_HASH", "tokenHash must be a 64-character SHA-256 hex string");
        }
        this.tokenHash = trimmedHash;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new BusinessRuleViolation("INVALID_TOKEN_EXPIRATION", "expiresAt must be strictly after issuedAt");
        }
        this.revokedAt = revokedAt;
    }

    public static RefreshToken issue(
            UUID id,
            UserId userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new RefreshToken(id, userId, tokenHash, issuedAt, expiresAt, null);
    }

    public static RefreshToken reconstitute(
            UUID id,
            UserId userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt
    ) {
        return new RefreshToken(id, userId, tokenHash, issuedAt, expiresAt, revokedAt);
    }

    public void revoke(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(this.expiresAt);
    }

    public boolean isActiveAt(Instant now) {
        return !isRevoked() && !isExpiredAt(now);
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
