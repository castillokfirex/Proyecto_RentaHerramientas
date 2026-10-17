package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import com.rentatools.billing.application.dto.ReservationBillingView;

import java.util.UUID;

/**
 * Respuesta HTTP con el estado financiero consolidado de una reserva.
 */
public record ReservationBillingResponse(
        UUID reservationId,
        InvoiceResponse rentalInvoice,
        InvoiceResponse settlementInvoice,
        SecurityDepositResponse securityDeposit
) {

    public static ReservationBillingResponse from(ReservationBillingView view) {
        return new ReservationBillingResponse(
                view.reservationId(),
                InvoiceResponse.from(view.rentalInvoice()),
                InvoiceResponse.from(view.settlementInvoice()),
                SecurityDepositResponse.from(view.securityDeposit())
        );
    }
}
