package com.rentatools.iam.application;

import com.rentatools.iam.application.port.out.AccessTokenIssuer;
import com.rentatools.iam.application.port.out.PasswordHasher;
import com.rentatools.iam.application.port.out.RefreshTokenRepository;
import com.rentatools.iam.application.port.out.SecureTokenGenerator;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fakes en memoria para los puertos de salida de IAM (Java 21 puro, sin Spring).
 */
final class IamTestFakes {

    private IamTestFakes() {
    }

    static final class InMemoryUserRepository implements UserRepository {

        private final Map<UserId, User> store = new LinkedHashMap<>();

        @Override
        public User save(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return store.values().stream()
                    .filter(u -> u.email().equals(email))
                    .findFirst();
        }

        @Override
        public boolean existsByEmail(Email email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public boolean existsByTaxId(TaxId taxId) {
            return store.values().stream()
                    .anyMatch(u -> u.providerProfile() != null && u.providerProfile().taxId().equals(taxId));
        }

        @Override
        public PageResult<User> findAll(PageQuery pageQuery, ActorRole roleFilter, UserStatus statusFilter) {
            List<User> filtered = store.values().stream()
                    .filter(u -> roleFilter == null || u.hasRole(roleFilter))
                    .filter(u -> statusFilter == null || u.status() == statusFilter)
                    .toList();

            int fromIndex = Math.min(pageQuery.offset(), filtered.size());
            int toIndex = Math.min(fromIndex + pageQuery.size(), filtered.size());
            List<User> pageItems = filtered.subList(fromIndex, toIndex);
            return PageResult.of(pageItems, pageQuery.page(), pageQuery.size(), filtered.size());
        }
    }

    static final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

        private final Map<UUID, RefreshToken> store = new LinkedHashMap<>();

        @Override
        public RefreshToken save(RefreshToken refreshToken) {
            store.put(refreshToken.id(), refreshToken);
            return refreshToken;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return store.values().stream()
                    .filter(t -> t.tokenHash().equals(tokenHash))
                    .findFirst();
        }

        @Override
        public void revokeAllActiveByUserId(UserId userId, Instant revokedAt) {
            store.values().stream()
                    .filter(t -> t.userId().equals(userId) && !t.isRevoked())
                    .forEach(t -> t.revoke(revokedAt));
        }

        List<RefreshToken> findByUserId(UserId userId) {
            return store.values().stream()
                    .filter(t -> t.userId().equals(userId))
                    .toList();
        }
    }

    static final class FakePasswordHasher implements PasswordHasher {

        @Override
        public HashedPassword hash(String rawPassword) {
            return HashedPassword.of("HASHED::" + rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, HashedPassword hashedPassword) {
            return ("HASHED::" + rawPassword).equals(hashedPassword.value());
        }
    }

    static final class FakeAccessTokenIssuer implements AccessTokenIssuer {

        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public IssuedAccessToken issue(User user, Instant issuedAt) {
            String token = "jwt-access-" + user.id().value() + "-" + counter.incrementAndGet();
            return new IssuedAccessToken(token, issuedAt.plus(Duration.ofMinutes(15)));
        }
    }

    static final class FakeSecureTokenGenerator implements SecureTokenGenerator {

        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public GeneratedRefreshToken generate() {
            String raw = "raw-refresh-token-" + counter.incrementAndGet();
            return new GeneratedRefreshToken(raw, hash(raw));
        }

        @Override
        public String hash(String rawToken) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
