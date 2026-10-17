package com.rentatools.shared.infrastructure.security;

import com.rentatools.shared.infrastructure.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de seguridad HTTP stateless con Resource Server JWT, CORS y seguridad por métodos.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            RestAuthErrorHandlers restAuthErrorHandlers
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthErrorHandlers)
                        .accessDeniedHandler(restAuthErrorHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/*.html",
                                "/*.css",
                                "/*.js",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico",
                                "/images/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/catalog/**",
                                "/api/v1/media/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/dev/token").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dev/clock").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/dev/clock").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/dev/clock").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(restAuthErrorHandlers)
                        .accessDeniedHandler(restAuthErrorHandlers));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
