package com.rentatools.rental.application.port.out;

import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia del agregado {@link Reservation}.
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(ReservationId id);

    Optional<Reservation> findByCode(ReservationCode code);

    boolean existsByCode(ReservationCode code);

    PageResult<Reservation> findByClient(ClientId clientId, ReservationStatus status, PageQuery pageQuery);

    PageResult<Reservation> findByProvider(ProviderId providerId, ReservationStatus status, PageQuery pageQuery);

    PageResult<Reservation> findAll(ReservationStatus status, PageQuery pageQuery);

    Optional<Reservation> findByReservationUnitId(com.rentatools.rental.domain.model.reservation.ReservationUnitId reservationUnitId);
}
