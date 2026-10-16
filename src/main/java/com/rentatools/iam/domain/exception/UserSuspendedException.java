package com.rentatools.iam.domain.exception;

import com.rentatools.iam.domain.model.UserId;
import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando un usuario suspendido intenta autenticarse o renovar su sesión.
 */
public class UserSuspendedException extends DomainException {

    public UserSuspendedException(UserId userId) {
        super("USER_SUSPENDED", "User account is suspended: " + userId.value());
    }
}
