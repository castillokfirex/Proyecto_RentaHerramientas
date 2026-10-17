package com.rentatools.inventory.application.port.in;

import com.rentatools.shared.domain.Actor;
import java.util.UUID;

/**
 * Puerto de entrada para eliminar una imagen de una herramienta.
 */
public interface RemoveToolImageUseCase {

    void removeToolImage(Actor actor, UUID toolId, UUID imageId);
}
