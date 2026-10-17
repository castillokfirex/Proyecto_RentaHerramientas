package com.rentatools.billing.infrastructure.adapter.in.web;

import com.rentatools.billing.application.port.in.AddDamageChargeUseCase;
import com.rentatools.billing.application.port.in.GetInvoiceQuery;
import com.rentatools.billing.application.port.in.RegisterPaymentUseCase;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.AddDamageChargeRequest;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.InvoiceResponse;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.RegisterPaymentRequest;
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
 * Controlador REST para consultar facturas, registrar pagos y agregar cargos por daño ({@code PLAN.md} §4.2).
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
public class InvoiceController {

    private final GetInvoiceQuery getInvoiceQuery;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final AddDamageChargeUseCase addDamageChargeUseCase;

    public InvoiceController(
            GetInvoiceQuery getInvoiceQuery,
            RegisterPaymentUseCase registerPaymentUseCase,
            AddDamageChargeUseCase addDamageChargeUseCase
    ) {
        this.getInvoiceQuery = getInvoiceQuery;
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.addDamageChargeUseCase = addDamageChargeUseCase;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public InvoiceResponse getInvoice(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return InvoiceResponse.from(getInvoiceQuery.getInvoice(actor, id));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('CLIENTE', 'PROVEEDOR', 'ADMIN')")
    public InvoiceResponse registerPayment(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody RegisterPaymentRequest request
    ) {
        return InvoiceResponse.from(registerPaymentUseCase.registerPayment(
                new RegisterPaymentUseCase.Command(
                        actor,
                        id,
                        Money.of(request.amount()),
                        request.method(),
                        request.reference()
                )
        ));
    }

    @PostMapping("/{id}/damage-charges")
    @PreAuthorize("hasAnyRole('PROVEEDOR', 'ADMIN')")
    public InvoiceResponse addDamageCharge(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody AddDamageChargeRequest request
    ) {
        return InvoiceResponse.from(addDamageChargeUseCase.addDamageCharge(
                new AddDamageChargeUseCase.Command(
                        actor,
                        id,
                        request.reservationUnitId(),
                        request.description(),
                        Money.of(request.amount())
                )
        ));
    }
}
