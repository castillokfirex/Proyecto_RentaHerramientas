package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import com.rentatools.iam.application.dto.RegisterClientCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de petición para el registro de clientes.
 */
public record RegisterClientRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone
) {

    public RegisterClientCommand toCommand() {
        return new RegisterClientCommand(email, password, fullName, phone);
    }
}
