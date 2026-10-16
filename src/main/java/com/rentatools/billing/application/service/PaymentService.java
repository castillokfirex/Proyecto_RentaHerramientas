package com.rentatools.billing.application.service;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.port.in.RegisterPaymentUseCase;
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
import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Servicio de aplicación para el registro de pagos y reembolsos sobre facturas ({@code PLAN.md} §3.4 pasos 2 y 6).
 */
public class PaymentService implements RegisterPaymentUseCase {

    private final InvoiceRepository invoiceRepository;
    private final SecurityDepositRepository securityDepositRepository;
    private final ReservationParticipantsPort reservationParticipantsPort;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final Clock clock;

    public PaymentService(
            InvoiceRepository invoiceRepository,
            SecurityDepositRepository securityDepositRepository,
            ReservationParticipantsPort reservationParticipantsPort,
            IntegrationEventPublisher integrationEventPublisher,
            Clock clock
    ) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.securityDepositRepository = Objects.requireNonNull(securityDepositRepository, "securityDepositRepository must not be null");
        this.reservationParticipantsPort = Objects.requireNonNull(reservationParticipantsPort, "reservationParticipantsPort must not be null");
        this.integrationEventPublisher = Objects.requireNonNull(integrationEventPublisher, "integrationEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public InvoiceView registerPayment(Command command) {
        Objects.requireNonNull(command, "command must not be null");

        Invoice invoice = invoiceRepository.findById(InvoiceId.of(command.invoiceId()))
                .orElseThrow(() -> new NotFoundException(
                        "INVOICE_NOT_FOUND",
                        "Invoice not found: " + command.invoiceId()
                ));

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(invoice.reservationId());
        participants.assertCanRecordFinancialMovement(command.actor(), command.method());

        SecurityDeposit deposit = securityDepositRepository.findByReservationId(invoice.reservationId())
                .orElse(null);

        Money retainedDeposit = resolveRetainedDeposit(invoice, deposit);
        Instant now = Instant.now(clock);

        invoice.registerPayment(
                command.amount(),
                command.method(),
                command.reference(),
                now,
                command.actor().userId(),
                retainedDeposit
        );
        Invoice savedInvoice = invoiceRepository.save(invoice);

        if (savedInvoice.type() == InvoiceType.RENTAL) {
            if (savedInvoice.isIssued()
                    && savedInvoice.balance().isZero()
                    && deposit != null
                    && deposit.isFullyCollected()
                    && !deposit.isSettled()) {
                integrationEventPublisher.publish(new ReservationClearedForDelivery(
                        Ids.newId(),
                        now,
                        savedInvoice.reservationId()
                ));
            }
        } else if (savedInvoice.type() == InvoiceType.SETTLEMENT) {
            if (savedInvoice.isIssued()
                    && savedInvoice.balance(retainedDeposit).isZero()
                    && deposit != null
                    && deposit.isSettled()) {
                integrationEventPublisher.publish(new ReservationSettled(
                        Ids.newId(),
                        now,
                        savedInvoice.reservationId(),
                        deposit.totalRefunded().amount(),
                        savedInvoice.total().amount()
                ));
            }
        }

        return InvoiceView.from(savedInvoice, retainedDeposit);
    }

    @Override
    public InvoiceView registerRefund(RefundCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Invoice invoice = invoiceRepository.findById(InvoiceId.of(command.invoiceId()))
                .orElseThrow(() -> new NotFoundException(
                        "INVOICE_NOT_FOUND",
                        "Invoice not found: " + command.invoiceId()
                ));

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(invoice.reservationId());
        participants.assertProviderOwnerOrAdmin(command.actor());

        SecurityDeposit deposit = securityDepositRepository.findByReservationId(invoice.reservationId())
                .orElse(null);
        Money retainedDeposit = resolveRetainedDeposit(invoice, deposit);

        Instant now = Instant.now(clock);
        invoice.registerRefund(
                command.amount(),
                command.method(),
                command.reference(),
                now,
                command.actor().userId()
        );
        Invoice savedInvoice = invoiceRepository.save(invoice);
        return InvoiceView.from(savedInvoice, retainedDeposit);
    }

    private static Money resolveRetainedDeposit(Invoice invoice, SecurityDeposit deposit) {
        if (invoice.type() == InvoiceType.SETTLEMENT && deposit != null) {
            return deposit.findRetentionForInvoice(invoice.id())
                    .map(Retention::amount)
                    .orElse(Money.ZERO);
        }
        return Money.ZERO;
    }
}
