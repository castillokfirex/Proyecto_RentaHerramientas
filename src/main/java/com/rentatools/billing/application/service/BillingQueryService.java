package com.rentatools.billing.application.service;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.application.port.in.GetInvoiceQuery;
import com.rentatools.billing.application.port.in.GetReservationBillingQuery;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort.Participants;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.Retention;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación para consultas de lectura de facturas y del estado financiero de una reserva.
 */
public class BillingQueryService implements GetInvoiceQuery, GetReservationBillingQuery {

    private final InvoiceRepository invoiceRepository;
    private final SecurityDepositRepository securityDepositRepository;
    private final ReservationParticipantsPort reservationParticipantsPort;

    public BillingQueryService(
            InvoiceRepository invoiceRepository,
            SecurityDepositRepository securityDepositRepository,
            ReservationParticipantsPort reservationParticipantsPort
    ) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.securityDepositRepository = Objects.requireNonNull(securityDepositRepository, "securityDepositRepository must not be null");
        this.reservationParticipantsPort = Objects.requireNonNull(reservationParticipantsPort, "reservationParticipantsPort must not be null");
    }

    @Override
    public InvoiceView getInvoice(Actor actor, UUID invoiceId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");

        Invoice invoice = invoiceRepository.findById(InvoiceId.of(invoiceId))
                .orElseThrow(() -> new NotFoundException(
                        "INVOICE_NOT_FOUND",
                        "Invoice not found: " + invoiceId
                ));

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(invoice.reservationId());
        participants.assertParticipantOrAdmin(actor);

        Money retainedDeposit = Money.ZERO;
        if (invoice.type() == InvoiceType.SETTLEMENT) {
            SecurityDeposit deposit = securityDepositRepository.findByReservationId(invoice.reservationId())
                    .orElse(null);
            if (deposit != null) {
                retainedDeposit = deposit.findRetentionForInvoice(invoice.id())
                        .map(Retention::amount)
                        .orElse(Money.ZERO);
            }
        }

        return InvoiceView.from(invoice, retainedDeposit);
    }

    @Override
    public ReservationBillingView getReservationBilling(Actor actor, UUID reservationId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(reservationId);
        participants.assertParticipantOrAdmin(actor);

        Invoice rentalInvoice = invoiceRepository.findByReservationIdAndType(reservationId, InvoiceType.RENTAL)
                .orElse(null);
        Invoice settlementInvoice = invoiceRepository.findByReservationIdAndType(reservationId, InvoiceType.SETTLEMENT)
                .orElse(null);
        SecurityDeposit deposit = securityDepositRepository.findByReservationId(reservationId)
                .orElse(null);

        if (rentalInvoice == null && settlementInvoice == null && deposit == null) {
            throw new NotFoundException(
                    "RESERVATION_BILLING_NOT_FOUND",
                    "No billing records found for reservation: " + reservationId
            );
        }

        return ReservationBillingView.of(reservationId, rentalInvoice, settlementInvoice, deposit);
    }
}
