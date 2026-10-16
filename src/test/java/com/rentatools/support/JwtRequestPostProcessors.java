package com.rentatools.support;

import com.rentatools.shared.domain.ActorRole;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Utilidades para simular tokens JWT autenticados con roles en pruebas {@code MockMvc}.
 */
public final class JwtRequestPostProcessors {

    private JwtRequestPostProcessors() {
    }

    public static JwtRequestPostProcessor asClient(UUID userId) {
        return asActor(userId, ActorRole.CLIENTE);
    }

    public static JwtRequestPostProcessor asClient() {
        return asClient(DevUsers.CLIENT_ID);
    }

    public static JwtRequestPostProcessor asProvider(UUID userId) {
        return asActor(userId, ActorRole.PROVEEDOR);
    }

    public static JwtRequestPostProcessor asProvider() {
        return asProvider(DevUsers.PROVIDER_1_ID);
    }

    public static JwtRequestPostProcessor asAdmin(UUID userId) {
        return asActor(userId, ActorRole.ADMIN);
    }

    public static JwtRequestPostProcessor asAdmin() {
        return asAdmin(DevUsers.ADMIN_ID);
    }

    public static JwtRequestPostProcessor asActor(UUID userId, ActorRole... roles) {
        List<String> roleNames = Arrays.stream(roles).map(ActorRole::name).toList();
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();

        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .issuer("rentatools")
                        .subject(userId.toString())
                        .claim("email", userId + "@rentatools.test")
                        .claim("roles", roleNames))
                .authorities(authorities);
    }
}
