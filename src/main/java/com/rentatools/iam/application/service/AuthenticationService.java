package com.rentatools.iam.application.service;

import com.rentatools.iam.application.dto.LoginCommand;
import com.rentatools.iam.application.dto.SessionTokens;
import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.port.in.LoginUseCase;
import com.rentatools.iam.application.port.in.LogoutUseCase;
import com.rentatools.iam.application.port.in.RefreshSessionUseCase;
import com.rentatools.iam.application.port.out.AccessTokenIssuer;
import com.rentatools.iam.application.port.out.AccessTokenIssuer.IssuedAccessToken;
import com.rentatools.iam.application.port.out.PasswordHasher;
import com.rentatools.iam.application.port.out.RefreshTokenRepository;
import com.rentatools.iam.application.port.out.SecureTokenGenerator;
import com.rentatools.iam.application.port.out.SecureTokenGenerator.GeneratedRefreshToken;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.exception.InvalidCredentialsException;
import com.rentatools.iam.domain.exception.InvalidRefreshTokenException;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.User;
import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Servicio de aplicación para inicio de sesión, rotación de refresh tokens (con detección de reutilización) y cierre de sesión.
 */
public class AuthenticationService implements LoginUseCase, RefreshSessionUseCase, LogoutUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureTokenGenerator secureTokenGenerator;
    private final Clock clock;

    public AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer,
            SecureTokenGenerator secureTokenGenerator,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer must not be null");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public SessionTokens login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Email email;
        try {
            email = Email.of(command.email());
        } catch (Exception e) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        user.assertCanLogin();

        Instant now = clock.instant();
        return issueSessionTokens(user, now);
    }

    @Override
    public SessionTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        Instant now = clock.instant();
        String tokenHash = secureTokenGenerator.hash(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        // Detección de reutilización: si un token ya revocado se vuelve a presentar,
        // se revocan inmediatamente todos los tokens activos del usuario.
        if (storedToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(storedToken.userId(), now);
            throw new InvalidRefreshTokenException("Refresh token reuse detected; all user sessions have been revoked");
        }

        if (storedToken.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = userRepository.findById(storedToken.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        user.assertCanLogin();

        // Rotación: se revoca el token actual y se emite uno nuevo
        storedToken.revoke(now);
        refreshTokenRepository.save(storedToken);

        return issueSessionTokens(user, now);
    }

    @Override
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        String tokenHash = secureTokenGenerator.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(now);
                refreshTokenRepository.save(token);
            }
        });
    }

    private SessionTokens issueSessionTokens(User user, Instant now) {
        IssuedAccessToken access = accessTokenIssuer.issue(user, now);
        GeneratedRefreshToken generatedRefresh = secureTokenGenerator.generate();
        Instant refreshExpiresAt = now.plus(secureTokenGenerator.refreshTokenTtl());

        RefreshToken refreshToken = RefreshToken.issue(
                Ids.newId(clock),
                user.id(),
                generatedRefresh.tokenHash(),
                now,
                refreshExpiresAt
        );
        refreshTokenRepository.save(refreshToken);

        return new SessionTokens(
                access.token(),
                access.expiresAt(),
                generatedRefresh.rawToken(),
                refreshExpiresAt,
                UserView.from(user)
        );
    }
}
