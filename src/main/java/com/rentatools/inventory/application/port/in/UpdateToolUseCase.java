package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.inventory.application.dto.UpdateToolCommand;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para actualizar la información descriptiva, garantía y categorías de una herramienta.
 */
public interface UpdateToolUseCase {

    ToolView updateTool(Actor actor, UpdateToolCommand command);
}
