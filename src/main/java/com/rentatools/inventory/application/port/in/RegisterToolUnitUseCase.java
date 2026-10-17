package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.RegisterToolUnitCommand;
import com.rentatools.inventory.application.dto.ToolUnitView;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para registrar una unidad física de una herramienta.
 */
public interface RegisterToolUnitUseCase {

    ToolUnitView registerToolUnit(Actor actor, RegisterToolUnitCommand command);
}
