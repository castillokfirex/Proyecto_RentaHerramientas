package com.rentatools.iam.infrastructure;

import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.RefreshTokenJpaEntity;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import com.rentatools.iam.infrastructure.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.rentatools.iam.infrastructure.adapter.out.security.BCryptPasswordHasher;
import com.rentatools.iam.infrastructure.adapter.out.security.Sha256SecureTokenGenerator;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.infrastructure.security.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IamInfrastructureAdaptersTest {

    @Test
    void mapperConvertsUserAndProviderProfileAndRefreshTokenRoundTrip() {
        UserPersistenceMapper mapper = new UserPersistenceMapper();
        Instant now = Instant.parse("2026-10-16T16:00:00Z");
        UserId adminId = UserId.newId();

        User provider = User.registerProvider(
                UserId.newId(),
                Email.of("prov@rentatools.com"),
                HashedPassword.of("$2a$10$hash"),
                "Proveedor Mapper",
                "+573001234567",
                "Equipos Mapper SAS",
                TaxId.of("900123456-7"),
                now
        );

        UserJpaEntity entity = mapper.toNewEntity(provider);
        assertThat(entity.getProviderProfile()).isNotNull();
        assertThat(entity.getProviderProfile().getUserId()).isEqualTo(provider.id().value());

        provider.approveProvider(adminId, now.plusSeconds(60));
        mapper.updateEntity(entity, provider);

        User reconstituted = mapper.toDomain(entity);
        assertThat(reconstituted.id()).isEqualTo(provider.id());
        assertThat(reconstituted.email()).isEqualTo(provider.email());
        assertThat(reconstituted.isApprovedProvider()).isTrue();
        assertThat(reconstituted.providerProfile().approvedBy()).isEqualTo(adminId);

        RefreshToken refreshToken = RefreshToken.issue(
                Ids.newId(),
                provider.id(),
                "f".repeat(64),
                now,
                now.plusSeconds(3600)
        );
        RefreshTokenJpaEntity tokenEntity = mapper.toEntity(refreshToken);
        RefreshToken tokenDomain = mapper.toDomain(tokenEntity);
        assertThat(tokenDomain.id()).isEqualTo(refreshToken.id());
        assertThat(tokenDomain.tokenHash()).isEqualTo(refreshToken.tokenHash());
    }

    @Test
    void bcryptPasswordHasherAndSha256TokenGeneratorWorkCorrectly() {
        BCryptPasswordHasher hasher = new BCryptPasswordHasher();
        HashedPassword hashed = hasher.hash("MySecurePassword123!");
        assertThat(hashed.value()).startsWith("$2a$");
        assertThat(hasher.matches("MySecurePassword123!", hashed)).isTrue();
        assertThat(hasher.matches("WrongPassword", hashed)).isFalse();

        JwtProperties jwtProps = new JwtProperties(
                "rentatools",
                "test-secret-key-at-least-32-characters-long-hs256",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
        Sha256SecureTokenGenerator tokenGen = new Sha256SecureTokenGenerator(jwtProps);
        var generated = tokenGen.generate();
        assertThat(generated.rawToken()).isNotBlank();
        assertThat(generated.tokenHash()).hasSize(64);
        assertThat(tokenGen.hash(generated.rawToken())).isEqualTo(generated.tokenHash());
    }
}
