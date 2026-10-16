package com.rentatools.iam.infrastructure.adapter.in.web.dto;

import com.rentatools.iam.application.dto.LoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de petición para autenticación de usuario.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
