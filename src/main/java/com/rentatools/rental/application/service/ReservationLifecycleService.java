package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.mapper.IntegrationEventMapper;
import com.rentatools.rental.application.mapper.ReservationViewMapper;
import com.rentatools.rental.application.port.in.CancelReservationUseCase;
import com.rentatools.rental.application.port.in.ConfirmReservationUseCase;
import com.rentatools.rental.application.port.in.RejectReservationUseCase;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.integration.rental.ReservationCancelled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación que gestiona las transiciones del ciclo de vida de una reserva
 * solicitadas por proveedor o cliente ({@link ConfirmReservationUseCase}, {@link RejectReservationUseCase},
 * {@link CancelReservationUseCase}) y publica los eventos de integración hacia {@code billing}.
 */
public class ReservationLifecycleService implements
        ConfirmReservationUseCase,
        RejectReservationUseCase,
        CancelReservationUseCase {

    private final ReservationRepository reservationRepository;
    private final UnitInventoryPort unitInventoryPort;
    private final ToolCatalogPort toolCatalogPort;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final Clock clock;

    public ReservationLifecycleService(
            ReservationRepository reservationRepository,
            UnitInventoryPort unitInventoryPort,
            ToolCatalogPort toolCatalogPort,
            IntegrationEventPublisher integrationEventPublisher,
            Clock clock
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository must not be null");
        this.unitInventoryPort = Objects.requireNonNull(unitInventoryPort, "unitInventoryPort must not be null");
        this.toolCatalogPort = Objects.requireNonNull(toolCatalogPort, "toolCatalogPort must not be null");
        this.integrationEventPublisher = Objects.requireNonNull(integrationEventPublisher, "integrationEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ReservationView confirmReservation(Actor actor, UUID reservationId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        Reservation reservation = loadReservationOrThrow(reservationId);
        reservation.confirm(actor, clock);
        Reservation saved = reservationRepository.save(reservation);

        ReservationView view = ReservationViewMapper.toView(saved, unitInventoryPort, toolCatalogPort);
        ReservationConfirmed integrationEvent = IntegrationEventMapper.toReservationConfirmed(
                saved,
                view.units(),
                actor.userId(),
                clock
        );
        integrationEventPublisher.publish(integrationEvent);

        return view;
    }

    @Override
    public ReservationView rejectReservation(Actor actor, UUID reservationId, String reason) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        Reservation reservation = loadReservationOrThrow(reservationId);
        reservation.reject(actor, reason, clock);
        Reservation saved = reservationRepository.save(reservation);

        return ReservationViewMapper.toView(saved, unitInventoryPort, toolCatalogPort);
    }

    @Override
    public ReservationView cancelReservation(Actor actor, UUID reservationId, String reason) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        Reservation reservation = loadReservationOrThrow(reservationId);
        ReservationStatus previousStatus = reservation.status();
        reservation.cancel(actor, reason, clock);
        Reservation saved = reservationRepository.save(reservation);

        ReservationCancelled integrationEvent = IntegrationEventMapper.toReservationCancelled(
                saved,
                previousStatus,
                actor.userId(),
                saved.statusReason(),
                clock
        );
        integrationEventPublisher.publish(integrationEvent);

        return ReservationViewMapper.toView(saved, unitInventoryPort, toolCatalogPort);
    }

    private Reservation loadReservationOrThrow(UUID reservationId) {
        return reservationRepository.findById(ReservationId.of(reservationId))
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId));
    }
}
