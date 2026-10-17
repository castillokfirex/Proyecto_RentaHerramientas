package com.rentatools.rental.application.port.out;

import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionId;
import com.rentatools.rental.domain.model.inspection.InspectionPhoto;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia del agregado {@link Inspection}.
 */
public interface InspectionRepository {

    Inspection save(Inspection inspection);

    Optional<Inspection> findById(InspectionId id);

    List<Inspection> findByReservationUnitId(ReservationUnitId reservationUnitId);

    List<Inspection> findByReservationId(ReservationId reservationId);

    Optional<InspectionPhoto> findPhotoById(UUID photoId);
}
