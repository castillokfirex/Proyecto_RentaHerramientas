package com.rentatools.billing.application.service;

import com.rentatools.billing.application.BillingSettings;
import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.application.port.in.CancelReservationBillingUseCase;
import com.rentatools.billing.application.port.in.OpenRentalBillingUseCase;
import com.rentatools.billing.application.port.out.InvoiceNumberAllocator;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.DepositTransaction;
import com.rentatools.billing.domain.model.deposit.DepositTransactionKind;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.billing.domain.model.invoice.Payment;
import com.rentatools.billing.domain.model.invoice.PaymentKind;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de aplicación encargado de abrir y cancelar la facturación de alquiler ({@code RENTAL})
 * y el depósito de garantía de una reserva ({@code PLAN.md} §3.4 pasos 1 y 3).
 */
public class RentalBillingService implements OpenRentalBillingUseCase, CancelReservationBillingUseCase {

    private final InvoiceRepository invoiceRepository;
    private final SecurityDepositRepository securityDepositRepository;
    private final InvoiceNumberAllocator invoiceNumberAllocator;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final BillingSettings billingSettings;
    private final Clock clock;

    public RentalBillingService(
            InvoiceRepository invoiceRepository,
            SecurityDepositRepository securityDepositRepository,
            InvoiceNumberAllocator invoiceNumberAllocator,
            IntegrationEventPublisher integrationEventPublisher,
            BillingSettings billingSettings,
            Clock clock
    ) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");
        this.securityDepositRepository = Objects.requireNonNull(securityDepositRepository, "securityDepositRepository must not be null");
        this.invoiceNumberAllocator = Objects.requireNonNull(invoiceNumberAllocator, "invoiceNumberAllocator must not be null");
        this.integrationEventPublisher = Objects.requireNonNull(integrationEventPublisher, "integrationEventPublisher must not be null");
        this.billingSettings = Objects.requireNonNull(billingSettings, "billingSettings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ReservationBillingView openRentalBilling(OpenRentalBillingUseCase.Command command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.units().isEmpty()) {
            throw new BusinessRuleViolation(
                    "EMPTY_RENTAL_UNITS",
                    "Cannot open rental billing without at least one reservation unit"
            );
        }

        Optional<Invoice> existingRental = invoiceRepository.findByReservationIdAndType(
                command.reservationId(),
                InvoiceType.RENTAL
        );
        if (existingRental.isPresent()) {
            SecurityDeposit existingDeposit = securityDepositRepository
                    .findByReservationId(command.reservationId())
                    .orElse(null);
            return ReservationBillingView.of(command.reservationId(), existingRental.get(), null, existingDeposit);
        }

        Instant now = Instant.now(clock);
        Invoice draftRental = Invoice.createDraft(
                command.reservationId(),
                InvoiceType.RENTAL,
                billingSettings.taxRate(),
                now
        );

        Money totalRequiredDeposit = Money.ZERO;
        for (UnitRentalCharge unit : command.units()) {
            String desc = buildRentalLineDescription(unit);
            draftRental.addLine(unit.reservationUnitId(), LineConcept.RENTAL, desc, 1L, unit.agreedAmount());
            totalRequiredDeposit = totalRequiredDeposit.plus(unit.depositAmount());
        }

        Invoice issuedRental = invoiceNumberAllocator.issueAndSave(draftRental, now, invoiceRepository);
        SecurityDeposit deposit = SecurityDeposit.open(command.reservationId(), totalRequiredDeposit, now);
        SecurityDeposit savedDeposit = securityDepositRepository.save(deposit);

        if (issuedRental.balance().isZero() && savedDeposit.isFullyCollected()) {
            integrationEventPublisher.publish(new ReservationClearedForDelivery(
                    Ids.newId(),
                    now,
                    command.reservationId()
            ));
        }

        return ReservationBillingView.of(command.reservationId(), issuedRental, null, savedDeposit);
    }

    @Override
    public void cancelReservationBilling(CancelReservationBillingUseCase.Command command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);

        Optional<Invoice> rentalOpt = invoiceRepository.findByReservationIdAndType(
                command.reservationId(),
                InvoiceType.RENTAL
        );
        if (rentalOpt.isPresent()) {
            Invoice rental = rentalOpt.get();
            if (rental.status() == InvoiceStatus.ISSUED) {
                rental.voidInvoice(now);
                Money netPaid = rental.netPaid();
                if (netPaid.isPositive()) {
                    PaymentMethod refundMethod = resolveInvoiceRefundMethod(rental);
                    rental.registerRefund(
                            netPaid,
                            refundMethod,
                            "Reembolso por cancelación de reserva",
                            now,
                            command.cancelledBy()
                    );
                }
                invoiceRepository.save(rental);
            }
        }

        Optional<SecurityDeposit> depositOpt = securityDepositRepository.findByReservationId(command.reservationId());
        if (depositOpt.isPresent()) {
            SecurityDeposit deposit = depositOpt.get();
            if (!deposit.isSettled()) {
                PaymentMethod refundMethod = resolveDepositRefundMethod(deposit);
                deposit.refundRemainingAndSettle(
                        refundMethod,
                        "Devolución de garantía por cancelación de reserva",
                        now,
                        command.cancelledBy()
                );
                securityDepositRepository.save(deposit);
            }
        }
    }

    private static String buildRentalLineDescription(UnitRentalCharge unit) {
        String tool = (unit.toolName() == null || unit.toolName().isBlank()) ? "Herramienta" : unit.toolName().trim();
        if (unit.serialNumber() != null && !unit.serialNumber().isBlank()) {
            return "Alquiler " + tool + " (S/N: " + unit.serialNumber().trim() + ")";
        }
        return "Alquiler " + tool;
    }

    private static PaymentMethod resolveInvoiceRefundMethod(Invoice invoice) {
        for (int i = invoice.payments().size() - 1; i >= 0; i--) {
            Payment p = invoice.payments().get(i);
            if (p.kind() == PaymentKind.PAYMENT) {
                return p.method();
            }
        }
        return PaymentMethod.SIMULATED;
    }

    private static PaymentMethod resolveDepositRefundMethod(SecurityDeposit deposit) {
        for (int i = deposit.transactions().size() - 1; i >= 0; i--) {
            DepositTransaction tx = deposit.transactions().get(i);
            if (tx.kind() == DepositTransactionKind.COLLECTION) {
                return tx.method();
            }
        }
        return PaymentMethod.SIMULATED;
    }
}
