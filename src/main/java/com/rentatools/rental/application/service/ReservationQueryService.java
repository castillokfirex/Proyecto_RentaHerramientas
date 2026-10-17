package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.mapper.ReservationViewMapper;
import com.rentatools.rental.application.port.in.GetReservationQuery;
import com.rentatools.rental.application.port.in.ListReservationsQuery;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación para las consultas {@link GetReservationQuery} y {@link ListReservationsQuery}.
 */
public class ReservationQueryService implements GetReservationQuery, ListReservationsQuery {

    private final ReservationRepository reservationRepository;
    private final UnitInventoryPort unitInventoryPort;
    private final ToolCatalogPort toolCatalogPort;

    public ReservationQueryService(
            ReservationRepository reservationRepository,
            UnitInventoryPort unitInventoryPort,
            ToolCatalogPort toolCatalogPort
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository must not be null");
        this.unitInventoryPort = Objects.requireNonNull(unitInventoryPort, "unitInventoryPort must not be null");
        this.toolCatalogPort = Objects.requireNonNull(toolCatalogPort, "toolCatalogPort must not be null");
    }

    @Override
    public ReservationView getReservation(Actor actor, UUID reservationId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        Reservation reservation = reservationRepository.findById(ReservationId.of(reservationId))
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId));
        reservation.assertParticipant(actor);

        return ReservationViewMapper.toView(reservation, unitInventoryPort, toolCatalogPort);
    }

    @Override
    public PageResult<ReservationView> listReservations(
            Actor actor,
            ReservationStatus status,
            PageQuery pageQuery
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");

        PageResult<Reservation> page;
        if (actor.is(ActorRole.ADMIN)) {
            page = reservationRepository.findAll(status, pageQuery);
        } else if (actor.is(ActorRole.PROVEEDOR) && !actor.is(ActorRole.CLIENTE)) {
            page = reservationRepository.findByProvider(ProviderId.of(actor.userId()), status, pageQuery);
        } else {
            page = reservationRepository.findByClient(ClientId.of(actor.userId()), status, pageQuery);
        }

        return page.map(r -> ReservationViewMapper.toView(r, unitInventoryPort, toolCatalogPort));
    }
}
