package com.rentatools.iam.domain.exception;

import com.rentatools.iam.domain.model.Email;
import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando se intenta registrar un correo electrónico que ya existe.
 */
public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException(Email email) {
        super("EMAIL_ALREADY_REGISTERED", "Email is already registered: " + email.value());
    }
}
