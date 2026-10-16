package com.rentatools.shared.infrastructure.web;

import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resuelve parámetros anotados con {@link CurrentActor} construyendo un {@link Actor}
 * a partir de la autenticación JWT actual en el {@link SecurityContextHolder}.
 */
@Component
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class)
                && Actor.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated actor in security context");
        }

        UUID userId = extractUserId(authentication);
        Set<ActorRole> roles = extractRoles(authentication);
        return new Actor(userId, roles);
    }

    private static UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return UUID.fromString(jwt.getSubject());
        }
        return UUID.fromString(authentication.getName());
    }

    private static Set<ActorRole> extractRoles(Authentication authentication) {
        Set<ActorRole> roles = EnumSet.noneOf(ActorRole.class);
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                String authName = authority.getAuthority();
                if (authName != null && authName.startsWith(ROLE_PREFIX)) {
                    String roleName = authName.substring(ROLE_PREFIX.length());
                    addRoleIfValid(roles, roleName);
                }
            }
        }
        if (roles.isEmpty() && authentication instanceof JwtAuthenticationToken jwtAuth) {
            List<String> claimRoles = jwtAuth.getToken().getClaimAsStringList("roles");
            if (claimRoles != null) {
                for (String roleName : claimRoles) {
                    addRoleIfValid(roles, roleName);
                }
            }
        }
        return roles;
    }

    private static void addRoleIfValid(Set<ActorRole> roles, String roleName) {
        try {
            roles.add(ActorRole.valueOf(roleName));
        } catch (IllegalArgumentException ignored) {
            // Ignora autoridades que no correspondan a ActorRole
        }
    }
}
