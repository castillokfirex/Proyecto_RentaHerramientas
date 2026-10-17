package com.rentatools.billing.infrastructure.adapter.in.web;

import com.rentatools.billing.application.port.in.CollectDepositUseCase;
import com.rentatools.billing.application.port.in.GetReservationBillingQuery;
import com.rentatools.billing.application.port.in.SettleReservationUseCase;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.CollectDepositRequest;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.ReservationBillingResponse;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.SecurityDepositResponse;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.SettleReservationRequest;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controlador REST para consultar el estado financiero de una reserva, cobrar su garantía
 * y ejecutar su liquidación final ({@code PLAN.md} §4.2).
 */
@RestController
@RequestMapping("/api/v1/billing/reservations")
public class ReservationBillingController {

    private final GetReservationBillingQuery getReservationBillingQuery;
    private final CollectDepositUseCase collectDepositUseCase;
    private final SettleReservationUseCase settleReservationUseCase;

    public ReservationBillingController(
            GetReservationBillingQuery getReservationBillingQuery,
            CollectDepositUseCase collectDepositUseCase,
            SettleReservationUseCase settleReservationUseCase
    ) {
        this.getReservationBillingQuery = getReservationBillingQuery;
        this.collectDepositUseCase = collectDepositUseCase;
        this.settleReservationUseCase = settleReservationUseCase;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public ReservationBillingResponse getReservationBilling(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return ReservationBillingResponse.from(
                getReservationBillingQuery.getReservationBilling(actor, id)
        );
    }

    @PostMapping("/{id}/deposit/collections")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public SecurityDepositResponse collectDeposit(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody CollectDepositRequest request
    ) {
        return SecurityDepositResponse.from(
                collectDepositUseCase.collectDeposit(
                        new CollectDepositUseCase.Command(
                                actor,
                                id,
                                Money.of(request.amount()),
                                request.method(),
                                request.reference()
                        )
                )
        );
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAnyRole('PROVEEDOR', 'ADMIN')")
    public ReservationBillingResponse settleReservation(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SettleReservationRequest request
    ) {
        PaymentMethod refundMethod = (request != null && request.refundMethod() != null)
                ? request.refundMethod()
                : PaymentMethod.SIMULATED;
        String refundReference = (request != null)
                ? request.refundReference()
                : "Liquidación de garantía";

        return ReservationBillingResponse.from(
                settleReservationUseCase.settleReservation(
                        new SettleReservationUseCase.Command(
                                actor,
                                id,
                                refundMethod,
                                refundReference
                        )
                )
        );
    }
}
