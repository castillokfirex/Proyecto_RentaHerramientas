package com.rentatools.iam.application.dto;

import java.util.Objects;

/**
 * Comando para registrar un nuevo usuario con rol {@code CLIENTE}.
 */
public record RegisterClientCommand(
        String email,
        String password,
        String fullName,
        String phone
) {

    public RegisterClientCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
    }
}
