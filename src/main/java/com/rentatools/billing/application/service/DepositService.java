package com.rentatools.billing.application.service;

import com.rentatools.billing.application.dto.SecurityDepositView;
import com.rentatools.billing.application.port.in.CollectDepositUseCase;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort.Participants;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de aplicación para el cobro del depósito de garantía de una reserva ({@code PLAN.md} §3.4 paso 2).
 */
public class DepositService implements CollectDepositUseCase {

    private final SecurityDepositRepository securityDepositRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReservationParticipantsPort reservationParticipantsPort;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final Clock clock;

    public DepositService(
            SecurityDepositRepository securityDepositRepository,
            InvoiceRepository invoiceRepository,
            ReservationParticipantsPort reservationParticipantsPort,
            IntegrationEventPublisher integrationEventPublisher,
            Clock clock
    ) {
        this.securityDepositRepository = Objects.requireNonNull(securityDepositRepository, "securityDepositRepository must not be null");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.reservationParticipantsPort = Objects.requireNonNull(reservationParticipantsPort, "reservationParticipantsPort must not be null");
        this.integrationEventPublisher = Objects.requireNonNull(integrationEventPublisher, "integrationEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public SecurityDepositView collectDeposit(Command command) {
        Objects.requireNonNull(command, "command must not be null");

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(command.reservationId());
        participants.assertCanRecordFinancialMovement(command.actor(), command.method());

        SecurityDeposit deposit = securityDepositRepository.findByReservationId(command.reservationId())
                .orElseThrow(() -> new NotFoundException(
                        "SECURITY_DEPOSIT_NOT_FOUND",
                        "Security deposit not found for reservation: " + command.reservationId()
                ));

        Instant now = Instant.now(clock);
        deposit.collect(
                command.amount(),
                command.method(),
                command.reference(),
                now,
                command.actor().userId()
        );
        SecurityDeposit savedDeposit = securityDepositRepository.save(deposit);

        if (savedDeposit.isFullyCollected() && !savedDeposit.isSettled()) {
            Optional<Invoice> rentalOpt = invoiceRepository.findByReservationIdAndType(
                    command.reservationId(),
                    InvoiceType.RENTAL
            );
            if (rentalOpt.isPresent()
                    && rentalOpt.get().isIssued()
                    && rentalOpt.get().balance().isZero()) {
                integrationEventPublisher.publish(new ReservationClearedForDelivery(
                        Ids.newId(),
                        now,
                        command.reservationId()
                ));
            }
        }

        return SecurityDepositView.from(savedDeposit);
    }
}
