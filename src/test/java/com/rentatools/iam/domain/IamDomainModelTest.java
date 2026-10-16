package com.rentatools.iam.domain;

import com.rentatools.iam.domain.event.ProviderApproved;
import com.rentatools.iam.domain.event.UserRegistered;
import com.rentatools.iam.domain.exception.UserSuspendedException;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.ProviderProfile;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamDomainModelTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");
    private static final String VALID_HASH = "a".repeat(64);

    @Test
    void emailNormalizesToLowercaseAndRejectsInvalidFormat() {
        Email email = Email.of("  Cliente.Prueba@RentaTools.COM  ");
        assertThat(email.value()).isEqualTo("cliente.prueba@rentatools.com");

        assertThatThrownBy(() -> Email.of("not-an-email"))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_EMAIL");

        assertThatThrownBy(() -> Email.of("   "))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_EMAIL");
    }

    @Test
    void taxIdNormalizesToUppercaseAndValidatesFormat() {
        TaxId taxId = TaxId.of(" 900123456-abc ");
        assertThat(taxId.value()).isEqualTo("900123456-ABC");

        assertThatThrownBy(() -> TaxId.of("12"))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_TAX_ID");

        assertThatThrownBy(() -> TaxId.of("900 123$456"))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_TAX_ID");
    }

    @Test
    void providerProfileEnforcesCoNullabilityOfApprovalFields() {
        TaxId taxId = TaxId.of("900111222-1");
        UserId adminId = UserId.newId();

        assertThatThrownBy(() -> new ProviderProfile("Empresa SAS", taxId, NOW, null))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_PROVIDER_APPROVAL_STATE");

        assertThatThrownBy(() -> new ProviderProfile("Empresa SAS", taxId, null, adminId))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_PROVIDER_APPROVAL_STATE");
    }

    @Test
    void userRegistrationAndProviderApprovalEnforceInvariantsAndEmitEvents() {
        UserId providerId = UserId.newId();
        UserId adminId = UserId.newId();

        User provider = User.registerProvider(
                providerId,
                Email.of("Prov@RentaTools.com"),
                HashedPassword.of("$2a$10$hash"),
                "Proveedor Uno",
                "+573001112233",
                "Equipos del Valle SAS",
                TaxId.of("900555666-7"),
                NOW
        );

        assertThat(provider.isApprovedProvider()).isFalse();
        assertThat(provider.providerProfile().isApproved()).isFalse();

        List<DomainEvent> regEvents = provider.pullDomainEvents();
        assertThat(regEvents).hasSize(1).first().isInstanceOf(UserRegistered.class);

        Instant approvalTime = NOW.plusSeconds(60);
        provider.approveProvider(adminId, approvalTime);

        assertThat(provider.isApprovedProvider()).isTrue();
        assertThat(provider.providerProfile().approvedAt()).isEqualTo(approvalTime);
        assertThat(provider.providerProfile().approvedBy()).isEqualTo(adminId);

        List<DomainEvent> approvalEvents = provider.pullDomainEvents();
        assertThat(approvalEvents).hasSize(1).first().isInstanceOf(ProviderApproved.class);

        // No se puede aprobar dos veces
        assertThatThrownBy(() -> provider.approveProvider(adminId, approvalTime.plusSeconds(10)))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("PROVIDER_ALREADY_APPROVED");
    }

    @Test
    void userRejectsInvalidProviderRoleCombinationsAndSuspendedLogin() {
        UserId userId = UserId.newId();
        Email email = Email.of("user@rentatools.com");
        HashedPassword hash = HashedPassword.of("$2a$10$hash");

        // PROVEEDOR sin ProviderProfile
        assertThatThrownBy(() -> User.reconstitute(
                userId, email, hash, "Sin Perfil", null,
                UserStatus.ACTIVE, Set.of(ActorRole.PROVEEDOR), null, NOW, NOW, 0L
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("PROVIDER_PROFILE_REQUIRED");

        // CLIENTE con ProviderProfile
        assertThatThrownBy(() -> User.reconstitute(
                userId, email, hash, "Cliente Con Perfil", null,
                UserStatus.ACTIVE, Set.of(ActorRole.CLIENTE),
                ProviderProfile.pending("Empresa", TaxId.of("900111222-3")), NOW, NOW, 0L
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("UNEXPECTED_PROVIDER_PROFILE");

        User client = User.registerClient(userId, email, hash, "Cliente Activo", null, NOW);
        client.assertCanLogin();

        client.changeStatus(UserStatus.SUSPENDED, NOW.plusSeconds(30));
        assertThatThrownBy(client::assertCanLogin)
                .isInstanceOf(UserSuspendedException.class)
                .extracting("code")
                .isEqualTo("USER_SUSPENDED");
    }

    @Test
    void refreshTokenValidatesExpirationAndRevocation() {
        UserId userId = UserId.newId();
        Instant expiresAt = NOW.plusSeconds(3600);

        assertThatThrownBy(() -> RefreshToken.issue(Ids.newId(), userId, VALID_HASH, NOW, NOW))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_TOKEN_EXPIRATION");

        RefreshToken token = RefreshToken.issue(Ids.newId(), userId, VALID_HASH, NOW, expiresAt);
        assertThat(token.isActiveAt(NOW)).isTrue();
        assertThat(token.isExpiredAt(expiresAt)).isTrue();

        Instant revokeTime = NOW.plusSeconds(120);
        token.revoke(revokeTime);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.revokedAt()).isEqualTo(revokeTime);
        assertThat(token.isActiveAt(revokeTime)).isFalse();
    }
}
