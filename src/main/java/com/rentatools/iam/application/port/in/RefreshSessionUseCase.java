package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.SessionTokens;

/**
 * Caso de uso para renovar una sesión rotando el refresh token y detectando reutilización.
 */
public interface RefreshSessionUseCase {

    SessionTokens refresh(String rawRefreshToken);
}
