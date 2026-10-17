package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.application.dto.RegisterInspectionCommand;
import com.rentatools.shared.domain.Actor;

/**
 * Caso de uso para registrar una inspección física de entrega o devolución.
 */
public interface RegisterInspectionUseCase {

    InspectionView registerInspection(Actor actor, RegisterInspectionCommand command);
}
