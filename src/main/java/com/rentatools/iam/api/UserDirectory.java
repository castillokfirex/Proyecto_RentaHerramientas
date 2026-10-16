package com.rentatools.iam.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público del contexto IAM para consulta de usuarios desde otros módulos.
 */
public interface UserDirectory {

    Optional<UserSummary> findById(UUID userId);

    boolean isActiveClient(UUID userId);

    boolean isApprovedProvider(UUID userId);
}
