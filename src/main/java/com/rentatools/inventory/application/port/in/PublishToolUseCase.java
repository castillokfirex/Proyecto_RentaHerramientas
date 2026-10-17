package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Puerto de entrada para publicar una herramienta (pasa a estado {@code ACTIVE}).
 */
public interface PublishToolUseCase {

    ToolView publishTool(Actor actor, UUID toolId);
}
