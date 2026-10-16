package com.rentatools.iam.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentatools.iam.application.port.out.RefreshTokenRepository;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.application.service.AuthenticationService;
import com.rentatools.iam.application.service.RegistrationService;
import com.rentatools.iam.application.service.UserAdministrationService;
import com.rentatools.iam.application.service.UserQueryService;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.iam.infrastructure.adapter.in.api.UserDirectoryAdapter;
import com.rentatools.iam.infrastructure.adapter.in.web.AdminUserController;
import com.rentatools.iam.infrastructure.adapter.in.web.AuthController;
import com.rentatools.iam.infrastructure.adapter.in.web.MeController;
import com.rentatools.iam.infrastructure.adapter.out.security.BCryptPasswordHasher;
import com.rentatools.iam.infrastructure.adapter.out.security.JwtAccessTokenIssuer;
import com.rentatools.iam.infrastructure.adapter.out.security.Sha256SecureTokenGenerator;
import com.rentatools.iam.infrastructure.bootstrap.AdminBootstrap;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración del flujo completo de sesión HTTP + JWT real + BCrypt real + SHA-256 real:
 * {@code register -> login -> me -> refresh -> logout -> refresh falla con 401} (y detección de reutilización).
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
        ClockConfig.class,
        BCryptPasswordHasher.class,
        JwtAccessTokenIssuer.class,
        Sha256SecureTokenGenerator.class,
        RegistrationService.class,
        AuthenticationService.class,
        UserAdministrationService.class,
        UserQueryService.class,
        UserDirectoryAdapter.class,
        AdminBootstrap.class,
        IamSessionFlowTest.InMemoryPersistenceConfig.class
})
@ActiveProfiles("test")
class IamSessionFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullSessionLifecycleRegisterLoginMeRefreshLogoutAndRevokedRefreshFailsWith401() throws Exception {
        // 1. Register client
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Flujo.Sesion@RentaTools.com",
                                  "password": "Password123!",
                                  "fullName": "Cliente Flujo",
                                  "phone": "+573005556677"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flujo.sesion@rentatools.com"))
                .andReturn();

        JsonNode registeredUser = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String userId = registeredUser.get("id").asText();

        // 2. Login con credenciales reales
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "flujo.sesion@rentatools.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken1 = loginJson.get("accessToken").asText();
        String refreshToken1 = loginJson.get("refreshToken").asText();

        // 3. GET /api/v1/users/me usando el JWT real emitido por JwtAccessTokenIssuer y validado por NimbusJwtDecoder
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("flujo.sesion@rentatools.com"));

        // 4. POST /api/v1/auth/refresh (rota refreshToken1 -> refreshToken2)
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String accessToken2 = refreshJson.get("accessToken").asText();
        String refreshToken2 = refreshJson.get("refreshToken").asText();
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        // 5. POST /api/v1/auth/logout revocando refreshToken2
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken2)))
                .andExpect(status().isOk());

        // 6. Intentar refrescar con refreshToken2 revocado tras logout -> falla con 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken2)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @TestConfiguration
    static class InMemoryPersistenceConfig {

        @Bean
        UserRepository userRepository() {
            return new UserRepository() {
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
                    return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
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
                    List<User> list = store.values().stream()
                            .filter(u -> roleFilter == null || u.hasRole(roleFilter))
                            .filter(u -> statusFilter == null || u.status() == statusFilter)
                            .toList();
                    return PageResult.of(list, pageQuery.page(), pageQuery.size(), list.size());
                }
            };
        }

        @Bean
        RefreshTokenRepository refreshTokenRepository() {
            return new RefreshTokenRepository() {
                private final Map<UUID, RefreshToken> store = new LinkedHashMap<>();

                @Override
                public RefreshToken save(RefreshToken refreshToken) {
                    store.put(refreshToken.id(), refreshToken);
                    return refreshToken;
                }

                @Override
                public Optional<RefreshToken> findByTokenHash(String tokenHash) {
                    return store.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
                }

                @Override
                public void revokeAllActiveByUserId(UserId userId, Instant revokedAt) {
                    store.values().stream()
                            .filter(t -> t.userId().equals(userId) && !t.isRevoked())
                            .forEach(t -> t.revoke(revokedAt));
                }
            };
        }
    }
}
