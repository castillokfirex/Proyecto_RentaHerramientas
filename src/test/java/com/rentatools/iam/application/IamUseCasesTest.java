package com.rentatools.iam.application;

import com.rentatools.iam.application.IamTestFakes.FakeAccessTokenIssuer;
import com.rentatools.iam.application.IamTestFakes.FakePasswordHasher;
import com.rentatools.iam.application.IamTestFakes.FakeSecureTokenGenerator;
import com.rentatools.iam.application.IamTestFakes.InMemoryRefreshTokenRepository;
import com.rentatools.iam.application.IamTestFakes.InMemoryUserRepository;
import com.rentatools.iam.application.dto.LoginCommand;
import com.rentatools.iam.application.dto.RegisterClientCommand;
import com.rentatools.iam.application.dto.RegisterProviderCommand;
import com.rentatools.iam.application.dto.SessionTokens;
import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.service.AuthenticationService;
import com.rentatools.iam.application.service.RegistrationService;
import com.rentatools.iam.application.service.UserAdministrationService;
import com.rentatools.iam.application.service.UserQueryService;
import com.rentatools.iam.domain.exception.EmailAlreadyRegisteredException;
import com.rentatools.iam.domain.exception.InvalidCredentialsException;
import com.rentatools.iam.domain.exception.InvalidRefreshTokenException;
import com.rentatools.iam.domain.exception.TaxIdAlreadyRegisteredException;
import com.rentatools.iam.domain.exception.UserSuspendedException;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.support.DevUsers;
import com.rentatools.support.fakes.MutableClock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamUseCasesTest {

    private InMemoryUserRepository userRepository;
    private InMemoryRefreshTokenRepository refreshTokenRepository;
    private FakePasswordHasher passwordHasher;
    private FakeAccessTokenIssuer accessTokenIssuer;
    private FakeSecureTokenGenerator secureTokenGenerator;
    private MutableClock clock;

    private RegistrationService registrationService;
    private AuthenticationService authenticationService;
    private UserAdministrationService userAdminService;
    private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        refreshTokenRepository = new InMemoryRefreshTokenRepository();
        passwordHasher = new FakePasswordHasher();
        accessTokenIssuer = new FakeAccessTokenIssuer();
        secureTokenGenerator = new FakeSecureTokenGenerator();
        clock = MutableClock.fixedAt("2026-10-16T15:00:00Z");

        registrationService = new RegistrationService(userRepository, passwordHasher, clock);
        authenticationService = new AuthenticationService(
                userRepository,
                refreshTokenRepository,
                passwordHasher,
                accessTokenIssuer,
                secureTokenGenerator,
                clock
        );
        userAdminService = new UserAdministrationService(userRepository, refreshTokenRepository, clock);
        userQueryService = new UserQueryService(userRepository);
    }

    @Test
    void registersClientNormalizingEmailAndRejectingDuplicates() {
        UserView client = registrationService.registerClient(new RegisterClientCommand(
                "  Ana.Cliente@RentaTools.com ",
                "Secret123!",
                "Ana Cliente",
                "+573001234567"
        ));

        assertThat(client.email()).isEqualTo("ana.cliente@rentatools.com");
        assertThat(client.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(client.roles()).containsExactly(ActorRole.CLIENTE);
        assertThat(client.providerProfile()).isNull();

        // Email duplicado (incluso con mayúsculas distintas)
        assertThatThrownBy(() -> registrationService.registerClient(new RegisterClientCommand(
                "ANA.CLIENTE@rentatools.com",
                "Secret123!",
                "Otra Ana",
                null
        )))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void registersProviderAsPendingAndRejectsDuplicateTaxId() {
        UserView provider = registrationService.registerProvider(new RegisterProviderCommand(
                "ventas@ferreteria.com",
                "Secret123!",
                "Carlos Proveedor",
                "+573109998877",
                "Ferretería Industrial SAS",
                "900888777-1"
        ));

        assertThat(provider.roles()).containsExactly(ActorRole.PROVEEDOR);
        assertThat(provider.providerProfile()).isNotNull();
        assertThat(provider.providerProfile().approved()).isFalse();
        assertThat(provider.providerProfile().approvedAt()).isNull();
        assertThat(provider.providerProfile().approvedBy()).isNull();

        assertThatThrownBy(() -> registrationService.registerProvider(new RegisterProviderCommand(
                "otro@ferreteria.com",
                "Secret123!",
                "Otro Proveedor",
                null,
                "Otra Empresa SAS",
                " 900888777-1 "
        )))
                .isInstanceOf(TaxIdAlreadyRegisteredException.class)
                .extracting("code")
                .isEqualTo("TAX_ID_ALREADY_REGISTERED");
    }

    @Test
    void loginValidatesCredentialsAndRejectsSuspendedUser() {
        UserView client = registrationService.registerClient(new RegisterClientCommand(
                "login.user@rentatools.com",
                "ValidPass123",
                "Usuario Login",
                null
        ));

        // Contraseña incorrecta
        assertThatThrownBy(() -> authenticationService.login(new LoginCommand(
                "login.user@rentatools.com",
                "WrongPass999"
        )))
                .isInstanceOf(InvalidCredentialsException.class);

        // Usuario inexistente
        assertThatThrownBy(() -> authenticationService.login(new LoginCommand(
                "nobody@rentatools.com",
                "ValidPass123"
        )))
                .isInstanceOf(InvalidCredentialsException.class);

        // Login exitoso
        SessionTokens session = authenticationService.login(new LoginCommand(
                "LOGIN.USER@rentatools.com",
                "ValidPass123"
        ));
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.user().id()).isEqualTo(client.id());

        // Suspender al usuario e intentar iniciar sesión
        userAdminService.changeStatus(DevUsers.ADMIN_ACTOR, client.id(), UserStatus.SUSPENDED);

        assertThatThrownBy(() -> authenticationService.login(new LoginCommand(
                "login.user@rentatools.com",
                "ValidPass123"
        )))
                .isInstanceOf(UserSuspendedException.class)
                .extracting("code")
                .isEqualTo("USER_SUSPENDED");
    }

    @Test
    void refreshRotatesTokenAndRevokesAllUserTokensWhenRevokedTokenIsReused() {
        UserView client = registrationService.registerClient(new RegisterClientCommand(
                "rotate@rentatools.com",
                "ValidPass123",
                "Usuario Rotación",
                null
        ));

        SessionTokens initialSession = authenticationService.login(new LoginCommand(
                "rotate@rentatools.com",
                "ValidPass123"
        ));
        String token1 = initialSession.refreshToken();

        clock.advance(Duration.ofMinutes(5));

        // Primera rotación: revoca token1 y emite token2
        SessionTokens rotatedSession = authenticationService.refresh(token1);
        String token2 = rotatedSession.refreshToken();
        assertThat(token2).isNotEqualTo(token1);

        // Simulamos también una segunda sesión activa del mismo usuario (token3)
        SessionTokens secondDeviceSession = authenticationService.login(new LoginCommand(
                "rotate@rentatools.com",
                "ValidPass123"
        ));
        String token3 = secondDeviceSession.refreshToken();

        clock.advance(Duration.ofMinutes(2));

        // Reutilización maliciosa de token1 (que ya estaba revocado):
        // Debe lanzar InvalidRefreshTokenException y revocar tanto token2 como token3.
        assertThatThrownBy(() -> authenticationService.refresh(token1))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("reuse detected");

        assertThat(refreshTokenRepository.findByUserId(UserId.of(client.id())))
                .hasSize(3)
                .allMatch(RefreshToken::isRevoked);

        // Ni token2 ni token3 pueden usarse ahora
        assertThatThrownBy(() -> authenticationService.refresh(token2))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> authenticationService.refresh(token3))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesRefreshTokenAndExpiredTokenIsRejected() {
        registrationService.registerClient(new RegisterClientCommand(
                "logout@rentatools.com",
                "ValidPass123",
                "Usuario Logout",
                null
        ));

        SessionTokens session1 = authenticationService.login(new LoginCommand("logout@rentatools.com", "ValidPass123"));
        authenticationService.logout(session1.refreshToken());

        assertThatThrownBy(() -> authenticationService.refresh(session1.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        SessionTokens session2 = authenticationService.login(new LoginCommand("logout@rentatools.com", "ValidPass123"));
        clock.advanceDays(8); // más allá de los 7 días de TTL

        assertThatThrownBy(() -> authenticationService.refresh(session2.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void adminApprovesProviderAndManagesUserStatusWhileEnforcingAuthorization() {
        UserView provider = registrationService.registerProvider(new RegisterProviderCommand(
                "prov.aprobacion@rentatools.com",
                "ValidPass123",
                "Proveedor Pendiente",
                null,
                "Maquinaria Pesada SAS",
                "900777666-5"
        ));

        Actor nonAdmin = new Actor(UUID.randomUUID(), Set.of(ActorRole.CLIENTE));

        // Un CLIENTE no puede aprobar proveedores ni cambiar estado
        assertThatThrownBy(() -> userAdminService.approveProvider(nonAdmin, provider.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> userAdminService.changeStatus(nonAdmin, provider.id(), UserStatus.SUSPENDED))
                .isInstanceOf(ForbiddenException.class);

        // Un ADMIN no puede suspenderse a sí mismo
        assertThatThrownBy(() -> userAdminService.changeStatus(
                DevUsers.ADMIN_ACTOR,
                DevUsers.ADMIN_ID,
                UserStatus.SUSPENDED
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("CANNOT_SUSPEND_SELF");

        // ADMIN aprueba proveedor
        UserView approved = userAdminService.approveProvider(DevUsers.ADMIN_ACTOR, provider.id());
        assertThat(approved.providerProfile().approved()).isTrue();
        assertThat(approved.providerProfile().approvedBy()).isEqualTo(DevUsers.ADMIN_ID);

        // Consultas GetCurrentUserQuery y ListUsersQuery
        UserView fetched = userQueryService.getById(provider.id());
        assertThat(fetched.providerProfile().approved()).isTrue();

        PageResult<UserView> providersPage = userQueryService.listUsers(
                PageQuery.of(0, 10),
                ActorRole.PROVEEDOR,
                UserStatus.ACTIVE
        );
        assertThat(providersPage.totalElements()).isEqualTo(1L);
        assertThat(providersPage.items()).extracting(UserView::id).containsExactly(provider.id());
    }
}
