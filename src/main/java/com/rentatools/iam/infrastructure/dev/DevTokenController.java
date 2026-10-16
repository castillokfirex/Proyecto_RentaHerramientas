package com.rentatools.iam.infrastructure.dev;

import com.rentatools.iam.api.UserDirectory;
import com.rentatools.iam.api.UserSummary;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.infrastructure.security.JwtProperties;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador exclusivo del perfil {@code dev} para emitir tokens JWT de prueba y verificar el {@link Actor} actual.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("dev")
public class DevTokenController {

    private final UserDirectory userDirectory;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public DevTokenController(
            UserDirectory userDirectory,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.userDirectory = userDirectory;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @PostMapping("/token")
    public DevTokenResponse issueDevToken(@Valid @RequestBody DevTokenRequest request) {
        UserSummary user = userDirectory.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + request.userId()));

        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());
        List<String> roleNames = user.roles().stream().map(ActorRole::name).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.email())
                .claim("roles", roleNames)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new DevTokenResponse(token, user.id(), user.email(), user.roles(), expiresAt);
    }

    @GetMapping("/whoami")
    public Actor whoami(@CurrentActor Actor actor) {
        return actor;
    }

    public record DevTokenRequest(@NotNull UUID userId) {
    }

    public record DevTokenResponse(
            String accessToken,
            UUID userId,
            String email,
            Set<ActorRole> roles,
            Instant expiresAt
    ) {
    }
}
