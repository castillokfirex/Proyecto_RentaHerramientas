package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.UserView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Caso de uso administrativo para aprobar el perfil de un proveedor registrado.
 */
public interface ApproveProviderUseCase {

    UserView approveProvider(Actor actor, UUID providerUserId);
}
