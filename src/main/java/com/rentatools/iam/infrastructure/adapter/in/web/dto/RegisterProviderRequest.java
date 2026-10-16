package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import com.rentatools.iam.application.dto.RegisterProviderCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de petición para el registro de proveedores.
 */
public record RegisterProviderRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone,
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(min = 5, max = 20) String taxId
) {

    public RegisterProviderCommand toCommand() {
        return new RegisterProviderCommand(email, password, fullName, phone, businessName, taxId);
    }
}
