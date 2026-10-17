package com.rentatools.rental.infrastructure.adapter.in.web;

import com.rentatools.rental.application.dto.CreateReservationCommand;
import com.rentatools.rental.application.dto.QuoteReservationCommand;
import com.rentatools.rental.application.dto.RequestedToolItem;
import com.rentatools.rental.application.port.in.CancelReservationUseCase;
import com.rentatools.rental.application.port.in.CreateReservationUseCase;
import com.rentatools.rental.application.port.in.GetReservationQuery;
import com.rentatools.rental.application.port.in.ListReservationsQuery;
import com.rentatools.rental.application.port.in.QuoteReservationUseCase;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.CreateReservationRequest;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.QuoteReservationRequest;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.QuoteResponse;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ReasonRequest;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ReservationResponse;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para cotización, creación, consulta y cancelación de reservas ({@code PLAN.md} §4.2).
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final QuoteReservationUseCase quoteReservationUseCase;
    private final CreateReservationUseCase createReservationUseCase;
    private final GetReservationQuery getReservationQuery;
    private final ListReservationsQuery listReservationsQuery;
    private final CancelReservationUseCase cancelReservationUseCase;

    public ReservationController(
            QuoteReservationUseCase quoteReservationUseCase,
            CreateReservationUseCase createReservationUseCase,
            GetReservationQuery getReservationQuery,
            ListReservationsQuery listReservationsQuery,
            CancelReservationUseCase cancelReservationUseCase
    ) {
        this.quoteReservationUseCase = Objects.requireNonNull(quoteReservationUseCase, "quoteReservationUseCase must not be null");
        this.createReservationUseCase = Objects.requireNonNull(createReservationUseCase, "createReservationUseCase must not be null");
        this.getReservationQuery = Objects.requireNonNull(getReservationQuery, "getReservationQuery must not be null");
        this.listReservationsQuery = Objects.requireNonNull(listReservationsQuery, "listReservationsQuery must not be null");
        this.cancelReservationUseCase = Objects.requireNonNull(cancelReservationUseCase, "cancelReservationUseCase must not be null");
    }

    @PostMapping("/quote")
    @PreAuthorize("hasRole('CLIENTE')")
    public QuoteResponse quote(
            @CurrentActor Actor actor,
            @Valid @RequestBody QuoteReservationRequest request
    ) {
        List<RequestedToolItem> items = request.items().stream()
                .map(i -> new RequestedToolItem(i.toolId(), i.quantity()))
                .toList();
        return QuoteResponse.from(quoteReservationUseCase.quoteReservation(
                actor,
                new QuoteReservationCommand(DateRange.of(request.startDate(), request.endDate()), items)
        ));
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ReservationResponse create(
            @CurrentActor Actor actor,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        List<RequestedToolItem> items = request.items().stream()
                .map(i -> new RequestedToolItem(i.toolId(), i.quantity()))
                .toList();
        return ReservationResponse.from(createReservationUseCase.createReservation(
                actor,
                new CreateReservationCommand(DateRange.of(request.startDate(), request.endDate()), items)
        ));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public PageResult<ReservationResponse> list(
            @CurrentActor Actor actor,
            @RequestParam(name = "status", required = false) ReservationStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return listReservationsQuery.listReservations(actor, status, PageQuery.of(page, size))
                .map(ReservationResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public ReservationResponse getById(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return ReservationResponse.from(getReservationQuery.getReservation(actor, id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CLIENTE')")
    public ReservationResponse cancel(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReasonRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return ReservationResponse.from(cancelReservationUseCase.cancelReservation(actor, id, reason));
    }
}
