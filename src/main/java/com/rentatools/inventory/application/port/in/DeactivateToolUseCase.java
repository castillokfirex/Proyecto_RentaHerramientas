package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Puerto de entrada para desactivar una herramienta (pasa a estado {@code INACTIVE}).
 */
public interface DeactivateToolUseCase {

    ToolView deactivateTool(Actor actor, UUID toolId);
}
