package com.rentatools.iam.infrastructure.adapter.out.security;

import com.rentatools.iam.application.port.out.PasswordHasher;
import com.rentatools.iam.domain.model.HashedPassword;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida que implementa {@link PasswordHasher} utilizando BCrypt.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher() {
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public HashedPassword hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        return HashedPassword.of(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, HashedPassword hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hashedPassword.value());
    }
}
