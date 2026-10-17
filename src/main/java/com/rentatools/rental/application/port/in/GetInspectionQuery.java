package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.shared.domain.Actor;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de detalle e histórico de inspecciones y fotos de una reserva.
 */
public interface GetInspectionQuery {

    InspectionView getInspection(Actor actor, UUID inspectionId);

    List<InspectionView> listInspectionsByReservation(Actor actor, UUID reservationId);

    InspectionPhotoResource getInspectionPhotoResource(Actor actor, UUID inspectionId, UUID photoId);

    record InspectionPhotoResource(
            InspectionPhotoView view,
            InputStream stream
    ) {
    }
}
