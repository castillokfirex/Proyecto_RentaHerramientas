package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.CreateToolCommand;
import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para crear una herramienta en estado {@code DRAFT}.
 */
public interface CreateToolUseCase {

    ToolView createTool(Actor actor, CreateToolCommand command);
}
