package com.rentatools.iam.domain.model;

import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para direcciones de correo electrónico normalizadas a minúsculas.
 */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        Objects.requireNonNull(value, "email must not be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessRuleViolation("INVALID_EMAIL", "Invalid email address: " + value);
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
