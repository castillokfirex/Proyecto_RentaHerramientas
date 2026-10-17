package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.AttachInspectionPhotoCommand;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.shared.domain.Actor;

/**
 * Caso de uso para adjuntar fotografías de evidencia a una inspección.
 */
public interface AttachInspectionPhotoUseCase {

    InspectionPhotoView attachPhoto(Actor actor, AttachInspectionPhotoCommand command);
}
