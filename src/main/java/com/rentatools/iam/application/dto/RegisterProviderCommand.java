package com.rentatools.iam.application.dto;

import java.util.Objects;

/**
 * Comando para registrar un nuevo usuario con rol {@code PROVEEDOR} y perfil pendiente de aprobación.
 */
public record RegisterProviderCommand(
        String email,
        String password,
        String fullName,
        String phone,
        String businessName,
        String taxId
) {

    public RegisterProviderCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(businessName, "businessName must not be null");
        Objects.requireNonNull(taxId, "taxId must not be null");
    }
}
