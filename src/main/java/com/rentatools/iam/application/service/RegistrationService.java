package com.rentatools.iam.application.service;

import com.rentatools.iam.application.dto.RegisterClientCommand;
import com.rentatools.iam.application.dto.RegisterProviderCommand;
import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.application.port.in.RegisterClientUseCase;
import com.rentatools.iam.application.port.in.RegisterProviderUseCase;
import com.rentatools.iam.application.port.out.PasswordHasher;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.exception.EmailAlreadyRegisteredException;
import com.rentatools.iam.domain.exception.TaxIdAlreadyRegisteredException;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Servicio de aplicación que implementa el registro de clientes y proveedores.
 */
public class RegistrationService implements RegisterClientUseCase, RegisterProviderUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public RegistrationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public UserView registerClient(RegisterClientCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateRawPassword(command.password());
        Email email = Email.of(command.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Instant now = clock.instant();
        HashedPassword passwordHash = passwordHasher.hash(command.password());
        User user = User.registerClient(
                UserId.of(Ids.newId(clock)),
                email,
                passwordHash,
                command.fullName(),
                command.phone(),
                now
        );

        User saved = userRepository.save(user);
        return UserView.from(saved);
    }

    @Override
    public UserView registerProvider(RegisterProviderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateRawPassword(command.password());
        Email email = Email.of(command.email());
        TaxId taxId = TaxId.of(command.taxId());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        if (userRepository.existsByTaxId(taxId)) {
            throw new TaxIdAlreadyRegisteredException(taxId);
        }

        Instant now = clock.instant();
        HashedPassword passwordHash = passwordHasher.hash(command.password());
        User user = User.registerProvider(
                UserId.of(Ids.newId(clock)),
                email,
                passwordHash,
                command.fullName(),
                command.phone(),
                command.businessName(),
                taxId,
                now
        );

        User saved = userRepository.save(user);
        return UserView.from(saved);
    }

    private static void validateRawPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolation(
                    "WEAK_PASSWORD",
                    "Password must have at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }
    }
}
