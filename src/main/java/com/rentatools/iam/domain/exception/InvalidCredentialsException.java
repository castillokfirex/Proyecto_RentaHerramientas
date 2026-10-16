package com.rentatools.iam.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando las credenciales de inicio de sesión son inválidas.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }
}
