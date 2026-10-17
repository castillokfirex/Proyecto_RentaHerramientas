package com.rentatools.inventory.infrastructure.adapter.in.web;

import com.rentatools.inventory.application.dto.ChangeUnitStatusCommand;
import com.rentatools.inventory.application.dto.RegisterToolUnitCommand;
import com.rentatools.inventory.application.port.in.ChangeUnitStatusUseCase;
import com.rentatools.inventory.application.port.in.RegisterToolUnitUseCase;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.ChangeUnitStatusRequest;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.RegisterToolUnitRequest;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.ToolUnitResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el registro y cambio de estado operativo de unidades físicas ({@code ToolUnit})
 * por parte del proveedor dueño.
 */
@RestController
@RequestMapping({"/api/v1/provider/tools/{toolId}/units", "/api/v1/tools/{toolId}/units"})
@PreAuthorize("hasRole('PROVEEDOR')")
public class ProviderToolUnitController {

    private final RegisterToolUnitUseCase registerToolUnitUseCase;
    private final ChangeUnitStatusUseCase changeUnitStatusUseCase;

    public ProviderToolUnitController(
            RegisterToolUnitUseCase registerToolUnitUseCase,
            ChangeUnitStatusUseCase changeUnitStatusUseCase
    ) {
        this.registerToolUnitUseCase = Objects.requireNonNull(registerToolUnitUseCase, "registerToolUnitUseCase must not be null");
        this.changeUnitStatusUseCase = Objects.requireNonNull(changeUnitStatusUseCase, "changeUnitStatusUseCase must not be null");
    }

    @PostMapping
    public ToolUnitResponse registerUnit(
            @CurrentActor Actor actor,
            @PathVariable UUID toolId,
            @Valid @RequestBody RegisterToolUnitRequest request
    ) {
        return ToolUnitResponse.from(registerToolUnitUseCase.registerToolUnit(
                actor,
                new RegisterToolUnitCommand(
                        toolId,
                        request.serialNumber(),
                        request.acquiredOn(),
                        request.notes()
                )
        ));
    }

    @PatchMapping("/{unitId}/status")
    public ToolUnitResponse changeUnitStatus(
            @CurrentActor Actor actor,
            @PathVariable UUID toolId,
            @PathVariable UUID unitId,
            @Valid @RequestBody ChangeUnitStatusRequest request
    ) {
        return ToolUnitResponse.from(changeUnitStatusUseCase.changeUnitStatus(
                actor,
                new ChangeUnitStatusCommand(
                        toolId,
                        unitId,
                        request.status(),
                        request.notes()
                )
        ));
    }
}
