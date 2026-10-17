package com.rentatools.billing.infrastructure;

import com.rentatools.billing.application.BillingSettings;
import com.rentatools.billing.application.BillingTestFakes.InMemoryInvoiceRepository;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.billing.infrastructure.adapter.out.persistence.MaxPlusOneInvoiceNumberAllocator;
import com.rentatools.billing.infrastructure.adapter.out.persistence.repository.SpringDataInvoiceRepository;
import com.rentatools.billing.infrastructure.adapter.out.rental.RentalReservationParticipantsAdapter;
import com.rentatools.billing.infrastructure.config.BillingProperties;
import com.rentatools.rental.api.ReservationDirectory;
import com.rentatools.rental.api.ReservationParticipants;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BillingInfrastructureAdaptersTest {

    @Test
    @DisplayName("MaxPlusOneInvoiceNumberAllocator reintenta ante colisión y asigna números distintos en dos hilos simultáneos")
    void retriesOnCollisionAndAllocatesDistinctNumbersAcrossTwoThreads() throws Exception {
        AtomicLong dbMaxNumber = new AtomicLong(10L);
        SpringDataInvoiceRepository springDataRepo = Mockito.mock(SpringDataInvoiceRepository.class);
        when(springDataRepo.findMaxInvoiceNumber()).thenAnswer(inv -> dbMaxNumber.get());

        MaxPlusOneInvoiceNumberAllocator allocator = new MaxPlusOneInvoiceNumberAllocator(springDataRepo);
        InMemoryInvoiceRepository baseRepo = new InMemoryInvoiceRepository();

        // Simulamos una colisión de clave única en el primer intento del primer hilo
        AtomicInteger saveAttempts = new AtomicInteger(0);
        InvoiceRepository collidingRepo = new InvoiceRepository() {
            @Override
            public Invoice save(Invoice invoice) {
                int attempt = saveAttempts.incrementAndGet();
                if (attempt == 1) {
                    // Otro proceso se adelantó y ocupó el número 11
                    dbMaxNumber.compareAndSet(10L, 11L);
                    throw new DataIntegrityViolationException("Simulated uq_invoices_number collision on number 11");
                }
                dbMaxNumber.updateAndGet(prev -> Math.max(prev, invoice.number().value()));
                return baseRepo.save(invoice);
            }

            @Override
            public Optional<Invoice> findById(InvoiceId id) {
                return baseRepo.findById(id);
            }

            @Override
            public Optional<Invoice> findByReservationIdAndType(UUID reservationId, InvoiceType type) {
                return baseRepo.findByReservationIdAndType(reservationId, type);
            }

            @Override
            public List<Invoice> findByReservationId(UUID reservationId) {
                return baseRepo.findByReservationId(reservationId);
            }

            @Override
            public List<Invoice> findAll() {
                return baseRepo.findAll();
            }

            @Override
            public void delete(InvoiceId id) {
                baseRepo.delete(id);
            }
        };

        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        Invoice draft1 = Invoice.createDraft(Ids.newId(), InvoiceType.RENTAL, new BigDecimal("0.19"), now);
        draft1.addLine(Ids.newId(), LineConcept.RENTAL, "Unidad 1", 1L, Money.of("100000"));

        Invoice draft2 = Invoice.createDraft(Ids.newId(), InvoiceType.RENTAL, new BigDecimal("0.19"), now);
        draft2.addLine(Ids.newId(), LineConcept.RENTAL, "Unidad 2", 1L, Money.of("120000"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Invoice> f1 = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return allocator.issueAndSave(draft1, now, collidingRepo);
            });
            Future<Invoice> f2 = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return allocator.issueAndSave(draft2, now, collidingRepo);
            });

            start.countDown();
            Invoice issued1 = f1.get(5, TimeUnit.SECONDS);
            Invoice issued2 = f2.get(5, TimeUnit.SECONDS);

            assertThat(issued1.number().value()).isNotEqualTo(issued2.number().value());
            assertThat(List.of(issued1.number().value(), issued2.number().value()))
                    .containsExactlyInAnyOrder(12L, 13L);
            assertThat(saveAttempts.get()).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("RentalReservationParticipantsAdapter traduce desde rental.api.ReservationDirectory y BillingProperties expone BillingSettings")
    void adaptsReservationDirectoryAndBillingProperties() {
        UUID reservationId = Ids.newId();
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();

        ReservationDirectory directory = id -> id.equals(reservationId)
                ? Optional.of(new ReservationParticipants(reservationId, "RES-99", clientId, providerId))
                : Optional.empty();

        RentalReservationParticipantsAdapter adapter = new RentalReservationParticipantsAdapter(directory);
        var participants = adapter.findByReservationId(reservationId);
        assertThat(participants).isPresent();
        assertThat(participants.get().reservationCode()).isEqualTo("RES-99");
        assertThat(participants.get().clientId()).isEqualTo(clientId);
        assertThat(participants.get().providerId()).isEqualTo(providerId);

        BillingProperties billingProperties = new BillingProperties();
        BillingSettings settings = billingProperties.billingSettings(
                new BillingProperties.Props(new BigDecimal("0.19"), new BigDecimal("1.50"))
        );
        assertThat(settings.taxRate()).isEqualByComparingTo("0.1900");
        assertThat(settings.lateFeeMultiplier()).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("RentalEventsListener traduce ReservationConfirmed, ReservationReturned y ReservationCancelled a los casos de uso")
    void rentalEventsListenerDelegatesToBillingUseCases() {
        var openRentalUseCase = Mockito.mock(com.rentatools.billing.application.port.in.OpenRentalBillingUseCase.class);
        var cancelBillingUseCase = Mockito.mock(com.rentatools.billing.application.port.in.CancelReservationBillingUseCase.class);
        var openSettlementUseCase = Mockito.mock(com.rentatools.billing.application.port.in.OpenSettlementUseCase.class);

        var listener = new com.rentatools.billing.infrastructure.adapter.in.event.RentalEventsListener(
                openRentalUseCase,
                cancelBillingUseCase,
                openSettlementUseCase
        );

        UUID reservationId = Ids.newId();
        UUID unitId = Ids.newId();
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        listener.onReservationConfirmed(new com.rentatools.shared.integration.rental.ReservationConfirmed(
                Ids.newId(),
                now,
                reservationId,
                "RES-10",
                clientId,
                providerId,
                java.time.LocalDate.of(2026, 6, 10),
                java.time.LocalDate.of(2026, 6, 12),
                providerId,
                List.of(new com.rentatools.shared.integration.rental.ReservationConfirmed.Line(
                        unitId,
                        "Taladro",
                        "SN-10",
                        new BigDecimal("100000.00"),
                        new BigDecimal("50000.00")
                ))
        ));
        Mockito.verify(openRentalUseCase).openRentalBilling(Mockito.argThat(cmd ->
                cmd.reservationId().equals(reservationId)
                        && cmd.units().size() == 1
                        && cmd.units().getFirst().agreedAmount().equals(Money.of("100000.00"))
        ));

        listener.onReservationReturned(new com.rentatools.shared.integration.rental.ReservationReturned(
                Ids.newId(),
                now,
                reservationId,
                providerId,
                List.of(new com.rentatools.shared.integration.rental.ReservationReturned.ReturnedUnit(
                        unitId,
                        "Taladro",
                        java.time.LocalDate.of(2026, 6, 14),
                        2,
                        new BigDecimal("25000.00"),
                        true,
                        false
                ))
        ));
        Mockito.verify(openSettlementUseCase).openSettlement(Mockito.argThat(cmd ->
                cmd.reservationId().equals(reservationId)
                        && cmd.units().size() == 1
                        && cmd.units().getFirst().lateDays() == 2
        ));

        listener.onReservationCancelled(new com.rentatools.shared.integration.rental.ReservationCancelled(
                Ids.newId(),
                now,
                reservationId,
                "CONFIRMED",
                clientId,
                "Cambio de planes"
        ));
        Mockito.verify(cancelBillingUseCase).cancelReservationBilling(Mockito.argThat(cmd ->
                cmd.reservationId().equals(reservationId)
                        && cmd.cancelledBy().equals(clientId)
        ));
    }
}
