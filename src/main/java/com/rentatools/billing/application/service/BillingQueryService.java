package com.rentatools.billing.application.service;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.application.dto.RevenueReportView;
import com.rentatools.billing.application.port.in.GetInvoiceQuery;
import com.rentatools.billing.application.port.in.GetReservationBillingQuery;
import com.rentatools.billing.application.port.in.GetRevenueReportQuery;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort.Participants;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.Retention;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.Payment;
import com.rentatools.billing.domain.model.invoice.PaymentKind;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación para consultas de lectura de facturas, estado financiero de una reserva
 * y reportes financieros para administración.
 */
public class BillingQueryService implements GetInvoiceQuery, GetReservationBillingQuery, GetRevenueReportQuery {

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

    @Override
    public RevenueReportView getRevenueReport(Actor actor, LocalDate from, LocalDate to) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.is(ActorRole.ADMIN)) {
            throw new ForbiddenException(
                    "ADMIN_REQUIRED",
                    "Only an ADMIN can access financial revenue reports"
            );
        }

        Instant fromInstant = (from != null) ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = (to != null) ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        List<Invoice> invoices = invoiceRepository.findAll();
        List<SecurityDeposit> deposits = securityDepositRepository.findAll();

        Money totalInvoiced = Money.ZERO;
        Money totalCollected = Money.ZERO;
        Map<PaymentMethod, Money> byMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            byMethod.put(method, Money.ZERO);
        }

        for (Invoice invoice : invoices) {
            if (invoice.isIssued() && isWithinRange(invoice.issuedAt(), fromInstant, toInstant)) {
                totalInvoiced = totalInvoiced.plus(invoice.total());
            }
            for (Payment payment : invoice.payments()) {
                if (isWithinRange(payment.occurredAt(), fromInstant, toInstant)) {
                    Money currentMethodTotal = byMethod.get(payment.method());
                    if (payment.kind() == PaymentKind.PAYMENT) {
                        totalCollected = totalCollected.plus(payment.amount());
                        byMethod.put(payment.method(), currentMethodTotal.plus(payment.amount()));
                    } else if (payment.kind() == PaymentKind.REFUND) {
                        totalCollected = totalCollected.minus(payment.amount());
                        byMethod.put(payment.method(), currentMethodTotal.minus(payment.amount()));
                    }
                }
            }
        }

        Money depositsRetained = Money.ZERO;
        Money depositsRefunded = Money.ZERO;
        for (SecurityDeposit deposit : deposits) {
            for (Retention retention : deposit.retentions()) {
                if (isWithinRange(retention.appliedAt(), fromInstant, toInstant)) {
                    depositsRetained = depositsRetained.plus(retention.amount());
                }
            }
            for (var tx : deposit.transactions()) {
                if (isWithinRange(tx.occurredAt(), fromInstant, toInstant)
                        && tx.kind() == com.rentatools.billing.domain.model.deposit.DepositTransactionKind.REFUND) {
                    depositsRefunded = depositsRefunded.plus(tx.amount());
                }
            }
        }

        Map<PaymentMethod, BigDecimal> breakdown = new EnumMap<>(PaymentMethod.class);
        for (Map.Entry<PaymentMethod, Money> entry : byMethod.entrySet()) {
            breakdown.put(entry.getKey(), entry.getValue().amount());
        }

        return new RevenueReportView(
                totalInvoiced.amount(),
                totalCollected.amount(),
                depositsRetained.amount(),
                depositsRefunded.amount(),
                breakdown
        );
    }

    private static boolean isWithinRange(Instant timestamp, Instant fromInclusive, Instant toExclusive) {
        if (timestamp == null) {
            return false;
        }
        if (fromInclusive != null && timestamp.isBefore(fromInclusive)) {
            return false;
        }
        if (toExclusive != null && !timestamp.isBefore(toExclusive)) {
            return false;
        }
        return true;
    }
}
