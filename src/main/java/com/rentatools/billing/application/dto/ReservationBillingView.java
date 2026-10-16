package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.deposit.Retention;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.shared.domain.Money;

import java.util.UUID;

/**
 * Vista consolidada del estado financiero (factura de alquiler, factura de liquidación y garantía) de una reserva.
 */
public record ReservationBillingView(
        UUID reservationId,
        InvoiceView rentalInvoice,
        InvoiceView settlementInvoice,
        SecurityDepositView securityDeposit
) {

    public static ReservationBillingView of(
            UUID reservationId,
            Invoice rentalInvoice,
            Invoice settlementInvoice,
            SecurityDeposit deposit
    ) {
        InvoiceView rentalView = rentalInvoice != null ? InvoiceView.from(rentalInvoice, Money.ZERO) : null;

        Money settlementRetained = Money.ZERO;
        if (settlementInvoice != null && deposit != null) {
            settlementRetained = deposit.findRetentionForInvoice(settlementInvoice.id())
                    .map(Retention::amount)
                    .orElse(Money.ZERO);
        }
        InvoiceView settlementView = settlementInvoice != null
                ? InvoiceView.from(settlementInvoice, settlementRetained)
                : null;
        SecurityDepositView depositView = deposit != null ? SecurityDepositView.from(deposit) : null;

        return new ReservationBillingView(reservationId, rentalView, settlementView, depositView);
    }
}
