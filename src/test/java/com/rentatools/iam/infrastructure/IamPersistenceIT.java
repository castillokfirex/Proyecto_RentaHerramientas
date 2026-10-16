package com.rentatools.iam.infrastructure;

import com.rentatools.iam.application.port.out.RefreshTokenRepository;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.iam.infrastructure.adapter.out.persistence.RefreshTokenPersistenceAdapter;
import com.rentatools.iam.infrastructure.adapter.out.persistence.UserPersistenceAdapter;
import com.rentatools.iam.infrastructure.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.Ids;
import com.rentatools.support.AbstractMySqlIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de persistencia JPA contra el esquema real {@code V1__iam.sql} en MySQL 8.4 (Testcontainers).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        UserPersistenceAdapter.class,
        RefreshTokenPersistenceAdapter.class,
        UserPersistenceMapper.class
})
class IamPersistenceIT extends AbstractMySqlIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void persistsAndLoadsClientProviderAndRefreshTokens() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        User admin = User.registerAdmin(
                UserId.newId(),
                Email.of("admin.it@rentatools.com"),
                HashedPassword.of("$2a$10$adminhash"),
                "Admin IT",
                null,
                now
        );
        userRepository.save(admin);

        User provider = User.registerProvider(
                UserId.newId(),
                Email.of("prov.it@rentatools.com"),
                HashedPassword.of("$2a$10$provhash"),
                "Proveedor IT",
                "+573001112233",
                "Constructora IT SAS",
                TaxId.of("900444555-6"),
                now
        );
        User savedProvider = userRepository.save(provider);
        assertThat(savedProvider.roles()).containsExactly(ActorRole.PROVEEDOR);
        assertThat(savedProvider.providerProfile()).isNotNull();
        assertThat(savedProvider.providerProfile().isApproved()).isFalse();
        assertThat(userRepository.existsByEmail(Email.of("prov.it@rentatools.com"))).isTrue();
        assertThat(userRepository.existsByTaxId(TaxId.of("900444555-6"))).isTrue();

        // Aprobar proveedor y actualizar en BD
        savedProvider.approveProvider(admin.id(), now.plusSeconds(10));
        User updatedProvider = userRepository.save(savedProvider);
        assertThat(updatedProvider.isApprovedProvider()).isTrue();
        assertThat(updatedProvider.providerProfile().approvedBy()).isEqualTo(admin.id());

        PageResult<User> providersPage = userRepository.findAll(PageQuery.of(0, 10), ActorRole.PROVEEDOR, UserStatus.ACTIVE);
        assertThat(providersPage.items()).extracting(User::id).contains(savedProvider.id());

        // Refresh tokens y revocación masiva
        String hash1 = "1".repeat(64);
        String hash2 = "2".repeat(64);
        refreshTokenRepository.save(RefreshToken.issue(Ids.newId(), savedProvider.id(), hash1, now, now.plusSeconds(3600)));
        refreshTokenRepository.save(RefreshToken.issue(Ids.newId(), savedProvider.id(), hash2, now, now.plusSeconds(3600)));

        assertThat(refreshTokenRepository.findByTokenHash(hash1)).isPresent();
        refreshTokenRepository.revokeAllActiveByUserId(savedProvider.id(), now.plusSeconds(30));

        assertThat(refreshTokenRepository.findByTokenHash(hash1).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash(hash2).orElseThrow().isRevoked()).isTrue();
    }
}
