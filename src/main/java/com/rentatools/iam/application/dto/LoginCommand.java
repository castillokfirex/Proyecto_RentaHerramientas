package com.rentatools.iam.application.dto;

import java.util.Objects;

/**
 * Comando de autenticación por credenciales (email y contraseña).
 */
public record LoginCommand(String email, String password) {

    public LoginCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
