package com.rentatools.iam.infrastructure.bootstrap;

import com.rentatools.iam.application.port.out.PasswordHasher;
import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializador que asegura la existencia de la cuenta de administrador inicial configurada en
 * {@code app.bootstrap.admin-email} y {@code app.bootstrap.admin-password}.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AppProperties appProperties;
    private final Clock clock;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            AppProperties appProperties,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.appProperties = Objects.requireNonNull(appProperties, "appProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = appProperties.bootstrap();
        if (bootstrap == null
                || bootstrap.adminEmail() == null || bootstrap.adminEmail().isBlank()
                || bootstrap.adminPassword() == null || bootstrap.adminPassword().isBlank()) {
            return;
        }

        Email adminEmail = Email.of(bootstrap.adminEmail());
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        Instant now = clock.instant();
        HashedPassword passwordHash = passwordHasher.hash(bootstrap.adminPassword());
        User admin = User.registerAdmin(
                UserId.of(Ids.newId(clock)),
                adminEmail,
                passwordHash,
                "System Administrator",
                null,
                now
        );
        userRepository.save(admin);
    }
}
