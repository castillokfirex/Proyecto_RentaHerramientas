package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.LoginCommand;
import com.rentatools.iam.application.dto.SessionTokens;

/**
 * Caso de uso para autenticar a un usuario y emitir un par de tokens (access + refresh).
 */
public interface LoginUseCase {

    SessionTokens login(LoginCommand command);
}
