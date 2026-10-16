package com.rentatools.iam.infrastructure.adapter.in.web;

import com.rentatools.iam.application.port.in.LoginUseCase;
import com.rentatools.iam.application.port.in.LogoutUseCase;
import com.rentatools.iam.application.port.in.RefreshSessionUseCase;
import com.rentatools.iam.application.port.in.RegisterClientUseCase;
import com.rentatools.iam.application.port.in.RegisterProviderUseCase;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.LoginRequest;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.RefreshRequest;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.RegisterClientRequest;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.RegisterProviderRequest;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.TokenResponse;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.UserResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para registro, inicio de sesión, rotación de refresh tokens y cierre de sesión.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterClientUseCase registerClientUseCase;
    private final RegisterProviderUseCase registerProviderUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthController(
            RegisterClientUseCase registerClientUseCase,
            RegisterProviderUseCase registerProviderUseCase,
            LoginUseCase loginUseCase,
            RefreshSessionUseCase refreshSessionUseCase,
            LogoutUseCase logoutUseCase
    ) {
        this.registerClientUseCase = registerClientUseCase;
        this.registerProviderUseCase = registerProviderUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/register/client")
    public UserResponse registerClient(@Valid @RequestBody RegisterClientRequest request) {
        return UserResponse.from(registerClientUseCase.registerClient(request.toCommand()));
    }

    @PostMapping("/register/provider")
    public UserResponse registerProvider(@Valid @RequestBody RegisterProviderRequest request) {
        return UserResponse.from(registerProviderUseCase.registerProvider(request.toCommand()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(loginUseCase.login(request.toCommand()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(refreshSessionUseCase.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public void logout(@CurrentActor Actor actor, @Valid @RequestBody RefreshRequest request) {
        logoutUseCase.logout(request.refreshToken());
    }
}
