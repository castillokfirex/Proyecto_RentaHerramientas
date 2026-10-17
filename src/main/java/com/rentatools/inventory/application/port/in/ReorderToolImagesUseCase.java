package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolImageView;
import com.rentatools.shared.domain.Actor;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada para reordenar las imágenes de una herramienta (la posición 0 es portada).
 */
public interface ReorderToolImagesUseCase {

    List<ToolImageView> reorderToolImages(Actor actor, UUID toolId, List<UUID> orderedImageIds);
}
