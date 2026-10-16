package com.rentatools.iam.infrastructure.adapter.out.security;

import com.rentatools.iam.application.port.out.AccessTokenIssuer;
import com.rentatools.iam.domain.model.User;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.infrastructure.security.JwtProperties;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida que emite access tokens JWT firmados con HS256 usando el {@link JwtEncoder} configurado en A0b.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtAccessTokenIssuer(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
    }

    @Override
    public IssuedAccessToken issue(User user, Instant issuedAt) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");

        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        List<String> roleNames = user.roles().stream()
                .map(ActorRole::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.id().value().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.email().value())
                .claim("roles", roleNames)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(tokenValue, expiresAt);
    }
}
