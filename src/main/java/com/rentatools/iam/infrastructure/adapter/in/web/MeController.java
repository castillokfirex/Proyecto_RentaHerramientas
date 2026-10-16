package com.rentatools.iam.infrastructure.adapter.in.web;

import com.rentatools.iam.application.port.in.GetCurrentUserQuery;
import com.rentatools.iam.infrastructure.adapter.in.web.dto.UserResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para consultar el perfil del usuario autenticado ({@code GET /api/v1/users/me}).
 */
@RestController
@RequestMapping("/api/v1/users")
public class MeController {

    private final GetCurrentUserQuery getCurrentUserQuery;

    public MeController(GetCurrentUserQuery getCurrentUserQuery) {
        this.getCurrentUserQuery = getCurrentUserQuery;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public UserResponse me(@CurrentActor Actor actor) {
        return UserResponse.from(getCurrentUserQuery.getById(actor.userId()));
    }
}
