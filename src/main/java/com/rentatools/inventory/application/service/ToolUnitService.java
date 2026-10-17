package com.rentatools.inventory.application.service;

import com.rentatools.inventory.application.dto.ChangeUnitStatusCommand;
import com.rentatools.inventory.application.dto.RegisterToolUnitCommand;
import com.rentatools.inventory.application.dto.ToolUnitView;
import com.rentatools.inventory.application.port.in.ChangeUnitStatusUseCase;
import com.rentatools.inventory.application.port.in.RegisterToolUnitUseCase;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.application.port.out.ToolUnitRepository;
import com.rentatools.inventory.domain.exception.DuplicateSerialNumberException;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import java.time.Clock;
import java.util.Objects;

/**
 * Servicio de aplicación que gestiona el registro y cambio de estado de unidades físicas ({@link ToolUnit}).
 */
public class ToolUnitService implements RegisterToolUnitUseCase, ChangeUnitStatusUseCase {

    private final ToolRepository toolRepository;
    private final ToolUnitRepository toolUnitRepository;
    private final Clock clock;

    public ToolUnitService(
            ToolRepository toolRepository,
            ToolUnitRepository toolUnitRepository,
            Clock clock
    ) {
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository must not be null");
        this.toolUnitRepository = Objects.requireNonNull(toolUnitRepository, "toolUnitRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ToolUnitView registerToolUnit(Actor actor, RegisterToolUnitCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        ToolId toolId = ToolId.of(command.toolId());
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new NotFoundException("TOOL_NOT_FOUND", "Tool not found: " + toolId));
        tool.assertOwnedBy(actor);

        SerialNumber serialNumber = SerialNumber.of(command.serialNumber());
        if (toolUnitRepository.existsByToolIdAndSerialNumber(toolId, serialNumber)) {
            throw new DuplicateSerialNumberException(
                    "Serial number '" + serialNumber + "' is already registered for tool " + toolId
            );
        }

        ToolUnit unit = ToolUnit.register(
                ToolUnitId.newId(),
                toolId,
                serialNumber,
                command.acquiredOn(),
                command.notes(),
                clock.instant()
        );

        ToolUnit saved = toolUnitRepository.save(unit);
        return ToolUnitView.from(saved);
    }

    @Override
    public ToolUnitView changeUnitStatus(Actor actor, ChangeUnitStatusCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        ToolId toolId = ToolId.of(command.toolId());
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new NotFoundException("TOOL_NOT_FOUND", "Tool not found: " + toolId));
        tool.assertOwnedBy(actor);

        ToolUnitId unitId = ToolUnitId.of(command.unitId());
        ToolUnit unit = toolUnitRepository.findById(unitId)
                .filter(u -> u.toolId().equals(toolId))
                .orElseThrow(() -> new NotFoundException(
                        "TOOL_UNIT_NOT_FOUND",
                        "ToolUnit " + unitId + " not found for tool " + toolId
                ));

        unit.changeStatus(command.newStatus(), command.notes(), clock.instant());

        ToolUnit saved = toolUnitRepository.save(unit);
        return ToolUnitView.from(saved);
    }
}
