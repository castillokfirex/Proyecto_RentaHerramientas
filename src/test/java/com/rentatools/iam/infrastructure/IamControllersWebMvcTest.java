package com.rentatools.iam.infrastructure;

import com.rentatools.iam.application.dto.SessionTokens;
import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.port.in.ApproveProviderUseCase;
import com.rentatools.iam.application.port.in.ChangeUserStatusUseCase;
import com.rentatools.iam.application.port.in.GetCurrentUserQuery;
import com.rentatools.iam.application.port.in.ListUsersQuery;
import com.rentatools.iam.application.port.in.LoginUseCase;
import com.rentatools.iam.application.port.in.LogoutUseCase;
import com.rentatools.iam.application.port.in.RefreshSessionUseCase;
import com.rentatools.iam.application.port.in.RegisterClientUseCase;
import com.rentatools.iam.application.port.in.RegisterProviderUseCase;
import com.rentatools.iam.domain.exception.InvalidCredentialsException;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.iam.infrastructure.adapter.in.web.AdminUserController;
import com.rentatools.iam.infrastructure.adapter.in.web.AuthController;
import com.rentatools.iam.infrastructure.adapter.in.web.MeController;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import com.rentatools.support.DevUsers;
import com.rentatools.support.JwtRequestPostProcessors;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code @WebMvcTest} para {@link AuthController}, {@link MeController} y {@link AdminUserController}
 * verificando códigos HTTP 200, 400, 401 y 403 con {@code jwt()}.
 */
@WebMvcTest(controllers = {
        AuthController.class,
        MeController.class,
        AdminUserController.class
})
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        RestAuthErrorHandlers.class,
        CurrentActorArgumentResolver.class,
        WebMvcConfig.class,
        GlobalExceptionHandler.class,
        ClockConfig.class
})
@ActiveProfiles("test")
class IamControllersWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterClientUseCase registerClientUseCase;

    @MockitoBean
    private RegisterProviderUseCase registerProviderUseCase;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private RefreshSessionUseCase refreshSessionUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private GetCurrentUserQuery getCurrentUserQuery;

    @MockitoBean
    private ListUsersQuery listUsersQuery;

    @MockitoBean
    private ChangeUserStatusUseCase changeUserStatusUseCase;

    @MockitoBean
    private ApproveProviderUseCase approveProviderUseCase;

    private static final Instant NOW = Instant.parse("2026-10-16T16:00:00Z");

    @Test
    void authControllerHandles200And400ValidationAnd401InvalidCredentials() throws Exception {
        UserView clientView = new UserView(
                DevUsers.CLIENT_ID,
                "cliente@rentatools.com",
                "Cliente Test",
                "+573001112233",
                UserStatus.ACTIVE,
                Set.of(ActorRole.CLIENTE),
                null,
                NOW
        );
        given(registerClientUseCase.registerClient(any())).willReturn(clientView);

        // 200 OK en registro válido
        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "cliente@rentatools.com",
                                  "password": "Password123!",
                                  "fullName": "Cliente Test",
                                  "phone": "+573001112233"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@rentatools.com"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"));

        // 400 Bad Request cuando falla Bean Validation (email inválido y contraseña corta)
        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "correo-invalido",
                                  "password": "123",
                                  "fullName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 401 Unauthorized cuando login falla por credenciales inválidas
        given(loginUseCase.login(any())).willThrow(new InvalidCredentialsException());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "cliente@rentatools.com",
                                  "password": "WrongPassword!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // 401 Unauthorized en /logout sin token JWT
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "some-token" }
                                """))
                .andExpect(status().isUnauthorized());

        // 200 OK en /login exitoso
        org.mockito.BDDMockito.willReturn(new SessionTokens(
                "jwt-token",
                NOW.plusSeconds(900),
                "refresh-token",
                NOW.plusSeconds(604800),
                clientView
        )).given(loginUseCase).login(any());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "cliente@rentatools.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void meControllerRequiresAuthenticationAndResolvesCurrentActor() throws Exception {
        // 401 sin token
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 200 con jwt() de cliente
        UserView clientView = new UserView(
                DevUsers.CLIENT_ID,
                "cliente@rentatools.com",
                "Cliente Test",
                null,
                UserStatus.ACTIVE,
                Set.of(ActorRole.CLIENTE),
                null,
                NOW
        );
        given(getCurrentUserQuery.getById(DevUsers.CLIENT_ID)).willReturn(clientView);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(JwtRequestPostProcessors.asClient(DevUsers.CLIENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DevUsers.CLIENT_ID.toString()))
                .andExpect(jsonPath("$.email").value("cliente@rentatools.com"));
    }

    @Test
    void adminUserControllerEnforces401And403AndAllows200ForAdmin() throws Exception {
        UUID providerId = DevUsers.PROVIDER_1_ID;

        // 401 sin token
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        // 403 cuando un CLIENTE o PROVEEDOR intenta acceder a endpoints de ADMIN
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/providers/{id}/approve", providerId)
                        .with(JwtRequestPostProcessors.asProvider()))
                .andExpect(status().isForbidden());

        // 200 cuando accede un ADMIN
        UserView approvedProviderView = new UserView(
                providerId,
                "prov@rentatools.com",
                "Proveedor Uno",
                null,
                UserStatus.ACTIVE,
                Set.of(ActorRole.PROVEEDOR),
                new UserView.ProviderProfileView("Empresa SAS", "900111222-1", true, NOW, DevUsers.ADMIN_ID),
                NOW
        );
        given(listUsersQuery.listUsers(any(), any(), any()))
                .willReturn(PageResult.of(List.of(approvedProviderView), 0, 20, 1L));
        given(approveProviderUseCase.approveProvider(any(), eq(providerId)))
                .willReturn(approvedProviderView);
        given(changeUserStatusUseCase.changeStatus(any(), eq(providerId), eq(UserStatus.SUSPENDED)))
                .willReturn(new UserView(
                        providerId,
                        "prov@rentatools.com",
                        "Proveedor Uno",
                        null,
                        UserStatus.SUSPENDED,
                        Set.of(ActorRole.PROVEEDOR),
                        approvedProviderView.providerProfile(),
                        NOW
                ));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/admin/providers/{id}/approve", providerId)
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerProfile.approved").value(true));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", providerId)
                        .with(JwtRequestPostProcessors.asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "SUSPENDED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }
}
