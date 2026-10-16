package com.rentatools.iam.application.port.in;

/**
 * Caso de uso para cerrar sesión revocando el refresh token indicado.
 */
public interface LogoutUseCase {

    void logout(String rawRefreshToken);
}
