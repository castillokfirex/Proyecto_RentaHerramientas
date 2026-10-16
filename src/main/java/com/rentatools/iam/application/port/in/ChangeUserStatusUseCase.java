package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Caso de uso administrativo para activar o suspender a un usuario.
 */
public interface ChangeUserStatusUseCase {

    UserView changeStatus(Actor actor, UUID targetUserId, UserStatus newStatus);
}
