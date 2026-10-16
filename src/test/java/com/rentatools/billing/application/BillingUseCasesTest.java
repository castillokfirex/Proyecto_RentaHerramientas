package com.rentatools.billing.application;

import com.rentatools.billing.application.BillingTestFakes.InMemoryInvoiceNumberAllocator;
import com.rentatools.billing.application.BillingTestFakes.InMemoryInvoiceRepository;
import com.rentatools.billing.application.BillingTestFakes.InMemoryReservationParticipantsPort;
import com.rentatools.billing.application.BillingTestFakes.InMemorySecurityDepositRepository;
import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.application.dto.SecurityDepositView;
import com.rentatools.billing.application.port.in.AddDamageChargeUseCase;
import com.rentatools.billing.application.port.in.CancelReservationBillingUseCase;
import com.rentatools.billing.application.port.in.CollectDepositUseCase;
import com.rentatools.billing.application.port.in.OpenRentalBillingUseCase;
import com.rentatools.billing.application.port.in.OpenRentalBillingUseCase.UnitRentalCharge;
import com.rentatools.billing.application.port.in.OpenSettlementUseCase;
import com.rentatools.billing.application.port.in.OpenSettlementUseCase.ReturnedUnitCharge;
import com.rentatools.billing.application.port.in.RegisterPaymentUseCase;
import com.rentatools.billing.application.port.in.SettleReservationUseCase;
import com.rentatools.billing.application.service.BillingQueryService;
import com.rentatools.billing.application.service.DepositService;
import com.rentatools.billing.application.service.PaymentService;
import com.rentatools.billing.application.service.RentalBillingService;
import com.rentatools.billing.application.service.SettlementService;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.billing.domain.service.LateFeePolicy;
import com.rentatools.billing.domain.service.SettlementCalculator;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import com.rentatools.support.fakes.InMemoryIntegrationEventPublisher;
import com.rentatools.support.fakes.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingUseCasesTest {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal LATE_FEE_MULTIPLIER = new BigDecimal("1.50");

    private InMemoryInvoiceRepository invoiceRepository;
    private InMemorySecurityDepositRepository depositRepository;
    private InMemoryInvoiceNumberAllocator numberAllocator;
    private InMemoryReservationParticipantsPort participantsPort;
    private InMemoryIntegrationEventPublisher eventPublisher;
    private MutableClock clock;

    private RentalBillingService rentalBillingService;
    private PaymentService paymentService;
    private DepositService depositService;
    private SettlementService settlementService;
    private BillingQueryService billingQueryService;

    private UUID reservationId;
    private UUID unit1Id;
    private UUID unit2Id;
    private Actor clientActor;
    private Actor providerActor;
    private Actor outsiderActor;

    @BeforeEach
    void setUp() {
        invoiceRepository = new InMemoryInvoiceRepository();
        depositRepository = new InMemorySecurityDepositRepository();
        numberAllocator = new InMemoryInvoiceNumberAllocator();
        participantsPort = new InMemoryReservationParticipantsPort();
        eventPublisher = new InMemoryIntegrationEventPublisher();
        clock = new MutableClock(Instant.parse("2026-06-01T10:00:00Z"));

        BillingSettings settings = new BillingSettings(TAX_RATE, LATE_FEE_MULTIPLIER);

        rentalBillingService = new RentalBillingService(
                invoiceRepository,
                depositRepository,
                numberAllocator,
                eventPublisher,
                settings,
                clock
        );
        paymentService = new PaymentService(
                invoiceRepository,
                depositRepository,
                participantsPort,
                eventPublisher,
                clock
        );
        depositService = new DepositService(
                depositRepository,
                invoiceRepository,
                participantsPort,
                eventPublisher,
                clock
        );
        settlementService = new SettlementService(
                invoiceRepository,
                depositRepository,
                numberAllocator,
                participantsPort,
                eventPublisher,
                new LateFeePolicy(),
                new SettlementCalculator(),
                settings,
                clock
        );
        billingQueryService = new BillingQueryService(
                invoiceRepository,
                depositRepository,
                participantsPort
        );

        reservationId = Ids.newId();
        unit1Id = Ids.newId();
        unit2Id = Ids.newId();
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();

        clientActor = new Actor(clientId, Set.of(ActorRole.CLIENTE));
        providerActor = new Actor(providerId, Set.of(ActorRole.PROVEEDOR));
        outsiderActor = new Actor(Ids.newId(), Set.of(ActorRole.CLIENTE));

        participantsPort.register(reservationId, "RES-0001", clientId, providerId);
    }

    @Test
    @DisplayName("OpenRentalBilling + Pago + Cobro de Garantía publica ReservationClearedForDelivery al completar ambos")
    void publishesReservationClearedForDeliveryWhenRentalPaidAndDepositCollected() {
        ReservationBillingView billing = rentalBillingService.openRentalBilling(
                new OpenRentalBillingUseCase.Command(
                        reservationId,
                        List.of(
                                new UnitRentalCharge(unit1Id, "Taladro", "SN-01", Money.of("100000"), Money.of("150000")),
                                new UnitRentalCharge(unit2Id, "Pulidora", "SN-02", Money.of("50000"), Money.of("50000"))
                        )
                )
        );

        // Subtotal = 150,000; IVA 19% = 28,500; Total = 178,500; Garantía requerida = 200,000
        assertThat(billing.rentalInvoice().status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(billing.rentalInvoice().number()).isEqualTo(1L);
        assertThat(billing.rentalInvoice().total()).isEqualByComparingTo("178500.00");
        assertThat(billing.securityDeposit().requiredAmount()).isEqualByComparingTo("200000.00");
        assertThat(eventPublisher.publishedEvents()).isEmpty();

        // Cliente paga la factura RENTAL completa (pero la garantía aún no se ha cobrado)
        InvoiceView paidRental = paymentService.registerPayment(new RegisterPaymentUseCase.Command(
                clientActor,
                billing.rentalInvoice().id(),
                Money.of("178500.00"),
                PaymentMethod.SIMULATED,
                "SIM-PAY-1"
        ));
        assertThat(paidRental.paid()).isTrue();
        assertThat(eventPublisher.publishedEvents()).isEmpty();

        // Cliente cobra la garantía completa -> ahora sí se publica ReservationClearedForDelivery
        SecurityDepositView collectedDeposit = depositService.collectDeposit(new CollectDepositUseCase.Command(
                clientActor,
                reservationId,
                Money.of("200000.00"),
                PaymentMethod.SIMULATED,
                "SIM-DEP-1"
        ));
        assertThat(collectedDeposit.fullyCollected()).isTrue();

        assertThat(eventPublisher.publishedEvents()).hasSize(1);
        assertThat(eventPublisher.publishedEvents().getFirst()).isInstanceOf(ReservationClearedForDelivery.class);
        ReservationClearedForDelivery clearedEvent =
                (ReservationClearedForDelivery) eventPublisher.publishedEvents().getFirst();
        assertThat(clearedEvent.reservationId()).isEqualTo(reservationId);
    }

    @Test
    @DisplayName("CancelReservationBilling anula la factura RENTAL, reembolsa lo pagado y liquida la garantía")
    void cancelsReservationBillingRefundingInvoiceAndDeposit() {
        ReservationBillingView billing = rentalBillingService.openRentalBilling(
                new OpenRentalBillingUseCase.Command(
                        reservationId,
                        List.of(new UnitRentalCharge(unit1Id, "Taladro", "SN-01", Money.of("100000"), Money.of("100000")))
                )
        );
        paymentService.registerPayment(new RegisterPaymentUseCase.Command(
                clientActor,
                billing.rentalInvoice().id(),
                Money.of("119000.00"),
                PaymentMethod.SIMULATED,
                "SIM-PAY"
        ));
        depositService.collectDeposit(new CollectDepositUseCase.Command(
                clientActor,
                reservationId,
                Money.of("100000.00"),
                PaymentMethod.SIMULATED,
                "SIM-DEP"
        ));

        rentalBillingService.cancelReservationBilling(new CancelReservationBillingUseCase.Command(
                reservationId,
                clientActor.userId(),
                "Cancelada por el cliente"
        ));

        ReservationBillingView afterCancel = billingQueryService.getReservationBilling(clientActor, reservationId);
        assertThat(afterCancel.rentalInvoice().status()).isEqualTo(InvoiceStatus.VOIDED);
        assertThat(afterCancel.rentalInvoice().netPaid()).isEqualByComparingTo("0.00");
        assertThat(afterCancel.securityDeposit().settled()).isTrue();
        assertThat(afterCancel.securityDeposit().balance()).isEqualByComparingTo("0.00");
        assertThat(afterCancel.securityDeposit().totalRefunded()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Liquidación con mora + daño cubiertos por la garantía publica ReservationSettled de inmediato")
    void settlesWithLateFeeAndDamageCoveredByDepositAndPublishesReservationSettled() {
        // Abrir billing inicial y cobrar garantía de 300,000
        rentalBillingService.openRentalBilling(
                new OpenRentalBillingUseCase.Command(
                        reservationId,
                        List.of(new UnitRentalCharge(unit1Id, "Taladro", "SN-01", Money.of("100000"), Money.of("300000")))
                )
        );
        depositService.collectDeposit(new CollectDepositUseCase.Command(
                clientActor,
                reservationId,
                Money.of("300000.00"),
                PaymentMethod.SIMULATED,
                "DEP-300"
        ));
        eventPublisher.clear();

        // 1) Abrir SETTLEMENT en borrador con 2 días de mora a tarifa diaria 20,000 (multiplicador 1.50 -> 30,000/día -> 60,000)
        InvoiceView draftSettlement = settlementService.openSettlement(new OpenSettlementUseCase.Command(
                reservationId,
                List.of(new ReturnedUnitCharge(unit1Id, "Taladro", 2, Money.of("20000")))
        ));
        assertThat(draftSettlement.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(draftSettlement.subtotal()).isEqualByComparingTo("60000.00");

        // 2) Proveedor agrega cargo por daño de 40,000 -> Subtotal = 100,000, IVA 19% = 19,000, Total = 119,000
        InvoiceView withDamage = settlementService.addDamageCharge(new AddDamageChargeUseCase.Command(
                providerActor,
                draftSettlement.id(),
                unit1Id,
                "Mandril golpeado",
                Money.of("40000.00")
        ));
        assertThat(withDamage.total()).isEqualByComparingTo("119000.00");

        // 3) Proveedor liquida la reserva: retención = 119,000, devolución al cliente = 181,000, saldo = 0
        ReservationBillingView settled = settlementService.settleReservation(
                new SettleReservationUseCase.Command(providerActor, reservationId)
        );
        assertThat(settled.settlementInvoice().status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(settled.settlementInvoice().number()).isEqualTo(2L);
        assertThat(settled.settlementInvoice().retainedDeposit()).isEqualByComparingTo("119000.00");
        assertThat(settled.settlementInvoice().balance()).isEqualByComparingTo("0.00");
        assertThat(settled.settlementInvoice().paid()).isTrue();
        assertThat(settled.securityDeposit().settled()).isTrue();
        assertThat(settled.securityDeposit().totalRetained()).isEqualByComparingTo("119000.00");
        assertThat(settled.securityDeposit().totalRefunded()).isEqualByComparingTo("181000.00");

        assertThat(eventPublisher.publishedEvents()).hasSize(1);
        assertThat(eventPublisher.publishedEvents().getFirst()).isInstanceOf(ReservationSettled.class);
        ReservationSettled settledEvent = (ReservationSettled) eventPublisher.publishedEvents().getFirst();
        assertThat(settledEvent.reservationId()).isEqualTo(reservationId);
        assertThat(settledEvent.depositRefunded()).isEqualByComparingTo("181000.00");
        assertThat(settledEvent.extraCharges()).isEqualByComparingTo("119000.00");
    }

    @Test
    @DisplayName("Liquidación cuando los cargos superan la garantía publica ReservationSettled solo tras el pago del saldo")
    void publishesReservationSettledAfterClientPaysRemainingSettlementBalance() {
        rentalBillingService.openRentalBilling(
                new OpenRentalBillingUseCase.Command(
                        reservationId,
                        List.of(new UnitRentalCharge(unit1Id, "Taladro", "SN-01", Money.of("100000"), Money.of("50000")))
                )
        );
        depositService.collectDeposit(new CollectDepositUseCase.Command(
                clientActor,
                reservationId,
                Money.of("50000.00"),
                PaymentMethod.SIMULATED,
                "DEP-50"
        ));
        eventPublisher.clear();

        InvoiceView draftSettlement = settlementService.openSettlement(new OpenSettlementUseCase.Command(
                reservationId,
                List.of(new ReturnedUnitCharge(unit1Id, "Taladro", 0, Money.of("20000")))
        ));
        // Daño de 100,000 + IVA 19% = 119,000.00 (garantía es solo 50,000.00)
        settlementService.addDamageCharge(new AddDamageChargeUseCase.Command(
                providerActor,
                draftSettlement.id(),
                unit1Id,
                "Motor quemado",
                Money.of("100000.00")
        ));

        ReservationBillingView settled = settlementService.settleReservation(
                new SettleReservationUseCase.Command(providerActor, reservationId)
        );
        // Queda saldo pendiente de 69,000.00 -> aún NO se publica ReservationSettled
        assertThat(settled.settlementInvoice().balance()).isEqualByComparingTo("69000.00");
        assertThat(eventPublisher.publishedEvents()).isEmpty();

        // Cuando el cliente paga los 69,000.00 restantes -> se publica ReservationSettled
        InvoiceView paidSettlement = paymentService.registerPayment(new RegisterPaymentUseCase.Command(
                clientActor,
                settled.settlementInvoice().id(),
                Money.of("69000.00"),
                PaymentMethod.SIMULATED,
                "PAY-REM-69"
        ));
        assertThat(paidSettlement.paid()).isTrue();

        assertThat(eventPublisher.publishedEvents()).hasSize(1);
        ReservationSettled settledEvent = (ReservationSettled) eventPublisher.publishedEvents().getFirst();
        assertThat(settledEvent.depositRefunded()).isEqualByComparingTo("0.00");
        assertThat(settledEvent.extraCharges()).isEqualByComparingTo("119000.00");
    }

    @Test
    @DisplayName("Liquidación sin cargos adicionales elimina el borrador SETTLEMENT y devuelve toda la garantía")
    void deletesEmptySettlementDraftAndRefundsFullDeposit() {
        rentalBillingService.openRentalBilling(
                new OpenRentalBillingUseCase.Command(
                        reservationId,
                        List.of(new UnitRentalCharge(unit1Id, "Taladro", "SN-01", Money.of("100000"), Money.of("80000")))
                )
        );
        depositService.collectDeposit(new CollectDepositUseCase.Command(
                clientActor,
                reservationId,
                Money.of("80000.00"),
                PaymentMethod.SIMULATED,
                "DEP-80"
        ));
        eventPublisher.clear();

        // Devolución a tiempo y sin daños -> borrador sin líneas
        settlementService.openSettlement(new OpenSettlementUseCase.Command(
                reservationId,
                List.of(new ReturnedUnitCharge(unit1Id, "Taladro", 0, Money.of("20000")))
        ));

        ReservationBillingView settled = settlementService.settleReservation(
                new SettleReservationUseCase.Command(providerActor, reservationId)
        );
        assertThat(settled.settlementInvoice()).isNull();
        assertThat(settled.securityDeposit().settled()).isTrue();
        assertThat(settled.securityDeposit().totalRefunded()).isEqualByComparingTo("80000.00");

        assertThat(eventPublisher.publishedEvents()).hasSize(1);
        ReservationSettled event = (ReservationSettled) eventPublisher.publishedEvents().getFirst();
        assertThat(event.depositRefunded()).isEqualByComparingTo("80000.00");
        assertThat(event.extraCharges()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Valida reglas de autorización de partícipes y medios de pago permitidos por rol")
    void enforcesParticipantAndPaymentMethodAuthorization() {
        ReservationBillingView billing = rentalBillingService.openRentalBilling(
                new OpenRentalBillingUseCase.Command(
                        reservationId,
                        List.of(new UnitRentalCharge(unit1Id, "Taladro", "SN-01", Money.of("100000"), Money.of("50000")))
                )
        );

        // Usuario ajeno no puede consultar ni pagar
        assertThatThrownBy(() -> billingQueryService.getReservationBilling(outsiderActor, reservationId))
                .isInstanceOf(ForbiddenException.class);

        // Cliente no puede registrar pagos en efectivo (solo SIMULATED)
        assertThatThrownBy(() -> paymentService.registerPayment(new RegisterPaymentUseCase.Command(
                clientActor,
                billing.rentalInvoice().id(),
                Money.of("50000"),
                PaymentMethod.CASH,
                "CASH-1"
        )))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("CLIENT_PAYMENT_METHOD_FORBIDDEN");
    }
}
