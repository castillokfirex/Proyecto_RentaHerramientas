package com.rentatools.rental.infrastructure.adapter.in.web;

import com.rentatools.rental.application.port.in.ConfirmReservationUseCase;
import com.rentatools.rental.application.port.in.RejectReservationUseCase;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ReasonRequest;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ReservationResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para las acciones del proveedor dueño sobre una reserva ({@code confirm} y {@code reject}).
 */
@RestController
@RequestMapping({"/api/v1/reservations", "/api/v1/provider/reservations"})
public class ProviderReservationController {

    private final ConfirmReservationUseCase confirmReservationUseCase;
    private final RejectReservationUseCase rejectReservationUseCase;

    public ProviderReservationController(
            ConfirmReservationUseCase confirmReservationUseCase,
            RejectReservationUseCase rejectReservationUseCase
    ) {
        this.confirmReservationUseCase = Objects.requireNonNull(confirmReservationUseCase, "confirmReservationUseCase must not be null");
        this.rejectReservationUseCase = Objects.requireNonNull(rejectReservationUseCase, "rejectReservationUseCase must not be null");
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ReservationResponse confirm(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return ReservationResponse.from(confirmReservationUseCase.confirmReservation(actor, id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ReservationResponse reject(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReasonRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return ReservationResponse.from(rejectReservationUseCase.rejectReservation(actor, id, reason));
    }
}
