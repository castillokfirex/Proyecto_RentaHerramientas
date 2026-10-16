package com.rentatools.iam.domain.exception;

import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando un refresh token es inexistente, expirado o ya fue revocado.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException(String message) {
        super("INVALID_REFRESH_TOKEN", message);
    }

    public InvalidRefreshTokenException() {
        this("Invalid, expired, or revoked refresh token");
    }
}
