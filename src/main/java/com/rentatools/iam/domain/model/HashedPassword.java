package com.rentatools.iam.domain.model;

import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.Objects;

/**
 * Value Object que encapsula un hash de contraseña (BCrypt). El texto plano nunca se almacena en el dominio.
 */
public record HashedPassword(String value) {

    public HashedPassword {
        Objects.requireNonNull(value, "password hash must not be null");
        if (value.isBlank()) {
            throw new BusinessRuleViolation("INVALID_PASSWORD_HASH", "Password hash must not be blank");
        }
    }

    public static HashedPassword of(String value) {
        return new HashedPassword(value);
    }
}
