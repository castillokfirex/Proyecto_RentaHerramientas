package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ChangeUnitStatusCommand;
import com.rentatools.inventory.application.dto.ToolUnitView;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para cambiar el estado operativo de una unidad física ({@code AVAILABLE <-> MAINTENANCE -> RETIRED}).
 */
public interface ChangeUnitStatusUseCase {

    ToolUnitView changeUnitStatus(Actor actor, ChangeUnitStatusCommand command);
}
