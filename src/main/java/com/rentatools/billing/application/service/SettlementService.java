package com.rentatools.billing.application.service;

import com.rentatools.billing.application.BillingSettings;
import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.application.port.in.AddDamageChargeUseCase;
import com.rentatools.billing.application.port.in.OpenSettlementUseCase;
import com.rentatools.billing.application.port.in.SettleReservationUseCase;
import com.rentatools.billing.application.port.out.InvoiceNumberAllocator;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort.Participants;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.billing.domain.service.LateFeePolicy;
import com.rentatools.billing.domain.service.SettlementCalculator;
import com.rentatools.billing.domain.service.SettlementCalculator.SettlementResult;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.integration.billing.ReservationSettled;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de aplicación encargado del flujo de liquidación ({@code SETTLEMENT}):
 * apertura de borrador con mora, adición de cargos por daños y liquidación final contra la garantía
 * ({@code PLAN.md} §3.4 pasos 4, 5 y 6).
 */
public class SettlementService implements
        OpenSettlementUseCase,
        AddDamageChargeUseCase,
        SettleReservationUseCase {

    private final InvoiceRepository invoiceRepository;
    private final SecurityDepositRepository securityDepositRepository;
    private final InvoiceNumberAllocator invoiceNumberAllocator;
    private final ReservationParticipantsPort reservationParticipantsPort;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final LateFeePolicy lateFeePolicy;
    private final SettlementCalculator settlementCalculator;
    private final BillingSettings billingSettings;
    private final Clock clock;

    public SettlementService(
            InvoiceRepository invoiceRepository,
            SecurityDepositRepository securityDepositRepository,
            InvoiceNumberAllocator invoiceNumberAllocator,
            ReservationParticipantsPort reservationParticipantsPort,
            IntegrationEventPublisher integrationEventPublisher,
            LateFeePolicy lateFeePolicy,
            SettlementCalculator settlementCalculator,
            BillingSettings billingSettings,
            Clock clock
    ) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.securityDepositRepository = Objects.requireNonNull(securityDepositRepository, "securityDepositRepository must not be null");
        this.invoiceNumberAllocator = Objects.requireNonNull(invoiceNumberAllocator, "invoiceNumberAllocator must not be null");
        this.reservationParticipantsPort = Objects.requireNonNull(reservationParticipantsPort, "reservationParticipantsPort must not be null");
        this.integrationEventPublisher = Objects.requireNonNull(integrationEventPublisher, "integrationEventPublisher must not be null");
        this.lateFeePolicy = Objects.requireNonNull(lateFeePolicy, "lateFeePolicy must not be null");
        this.settlementCalculator = Objects.requireNonNull(settlementCalculator, "settlementCalculator must not be null");
        this.billingSettings = Objects.requireNonNull(billingSettings, "billingSettings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public InvoiceView openSettlement(OpenSettlementUseCase.Command command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<Invoice> existing = invoiceRepository.findByReservationIdAndType(
                command.reservationId(),
                InvoiceType.SETTLEMENT
        );
        if (existing.isPresent()) {
            return InvoiceView.from(existing.get());
        }

        Instant now = Instant.now(clock);
        Invoice draftSettlement = Invoice.createDraft(
                command.reservationId(),
                InvoiceType.SETTLEMENT,
                billingSettings.taxRate(),
                now
        );

        for (ReturnedUnitCharge unit : command.units()) {
            lateFeePolicy.buildLateFeeLineIfLate(
                    unit.reservationUnitId(),
                    unit.toolName(),
                    unit.lateDays(),
                    unit.dailyRate(),
                    billingSettings.lateFeeMultiplier()
            ).ifPresent(draftSettlement::addLine);
        }

        Invoice saved = invoiceRepository.save(draftSettlement);
        return InvoiceView.from(saved);
    }

    @Override
    public InvoiceView addDamageCharge(AddDamageChargeUseCase.Command command) {
        Objects.requireNonNull(command, "command must not be null");

        Invoice settlement = invoiceRepository.findById(InvoiceId.of(command.invoiceId()))
                .orElseThrow(() -> new NotFoundException(
                        "INVOICE_NOT_FOUND",
                        "Invoice not found: " + command.invoiceId()
                ));

        if (settlement.type() != InvoiceType.SETTLEMENT) {
            throw new BusinessRuleViolation(
                    "DAMAGE_CHARGE_ONLY_ON_SETTLEMENT",
                    "Damage charges can only be added to SETTLEMENT invoices"
            );
        }

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(settlement.reservationId());
        participants.assertProviderOwnerOrAdmin(command.actor());

        settlement.addLine(
                command.reservationUnitId(),
                LineConcept.DAMAGE,
                command.description(),
                1L,
                command.amount()
        );
        Invoice saved = invoiceRepository.save(settlement);
        return InvoiceView.from(saved);
    }

    @Override
    public ReservationBillingView settleReservation(SettleReservationUseCase.Command command) {
        Objects.requireNonNull(command, "command must not be null");

        Participants participants = reservationParticipantsPort.getRequiredByReservationId(command.reservationId());
        participants.assertProviderOwnerOrAdmin(command.actor());

        SecurityDeposit deposit = securityDepositRepository.findByReservationId(command.reservationId())
                .orElseThrow(() -> new NotFoundException(
                        "SECURITY_DEPOSIT_NOT_FOUND",
                        "Security deposit not found for reservation: " + command.reservationId()
                ));

        Invoice rentalInvoice = invoiceRepository.findByReservationIdAndType(
                command.reservationId(),
                InvoiceType.RENTAL
        ).orElse(null);

        Invoice settlementInvoice = invoiceRepository.findByReservationIdAndType(
                command.reservationId(),
                InvoiceType.SETTLEMENT
        ).orElse(null);

        Instant now = Instant.now(clock);

        if (settlementInvoice == null || settlementInvoice.lines().isEmpty()) {
            if (settlementInvoice != null) {
                invoiceRepository.delete(settlementInvoice.id());
                settlementInvoice = null;
            }
            SettlementResult result = settlementCalculator.settle(
                    null,
                    null,
                    deposit,
                    command.refundMethod(),
                    command.refundReference(),
                    now,
                    command.actor().userId()
            );
            SecurityDeposit savedDeposit = securityDepositRepository.save(deposit);

            integrationEventPublisher.publish(new ReservationSettled(
                    Ids.newId(),
                    now,
                    command.reservationId(),
                    result.refundToClient().amount(),
                    Money.ZERO.amount()
            ));

            return ReservationBillingView.of(command.reservationId(), rentalInvoice, null, savedDeposit);
        }

        if (settlementInvoice.isDraft()) {
            settlementInvoice = invoiceNumberAllocator.issueAndSave(settlementInvoice, now, invoiceRepository);
        }

        SettlementResult result = settlementCalculator.settle(
                settlementInvoice,
                settlementInvoice.number(),
                deposit,
                command.refundMethod(),
                command.refundReference(),
                now,
                command.actor().userId()
        );
        SecurityDeposit savedDeposit = securityDepositRepository.save(deposit);

        if (settlementInvoice.balance(result.retainedFromDeposit()).isZero()) {
            integrationEventPublisher.publish(new ReservationSettled(
                    Ids.newId(),
                    now,
                    command.reservationId(),
                    result.refundToClient().amount(),
                    settlementInvoice.total().amount()
            ));
        }

        return ReservationBillingView.of(
                command.reservationId(),
                rentalInvoice,
                settlementInvoice,
                savedDeposit
        );
    }
}
