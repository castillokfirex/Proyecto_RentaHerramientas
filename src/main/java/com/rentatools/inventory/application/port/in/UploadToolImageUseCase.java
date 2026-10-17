package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolImageView;
import com.rentatools.inventory.application.dto.UploadToolImageCommand;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para subir una nueva imagen de una herramienta.
 */
public interface UploadToolImageUseCase {

    ToolImageView uploadToolImage(Actor actor, UploadToolImageCommand command);
}
