package com.rentatools.rental.infrastructure;

import com.rentatools.rental.api.ReservationParticipants;
import com.rentatools.rental.application.dto.CreateReservationCommand;
import com.rentatools.rental.application.dto.QuoteReservationCommand;
import com.rentatools.rental.application.dto.RequestedToolItem;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitAvailabilityPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.application.port.out.UserVerificationPort;
import com.rentatools.rental.application.service.ReservationBookingService;
import com.rentatools.rental.application.service.ReservationLifecycleService;
import com.rentatools.rental.application.service.ReservationQueryService;
import com.rentatools.rental.domain.exception.InsufficientAvailabilityException;
import com.rentatools.rental.domain.model.pricing.Quote;
import com.rentatools.rental.domain.model.pricing.RateCard;
import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.rental.domain.service.PricingPolicy;
import com.rentatools.rental.domain.service.UnitAllocator;
import com.rentatools.rental.infrastructure.adapter.in.api.ReservationDirectoryAdapter;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.infrastructure.event.SpringIntegrationEventPublisher;
import com.rentatools.shared.integration.rental.ReservationCancelled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración que verifica:
 * <ol>
 *   <li>La publicación síncrona y el payload exacto de {@link ReservationConfirmed} y {@link ReservationCancelled}
 *       capturados por un listener {@code @EventListener} de prueba.</li>
 *   <li>La serialización concurrente al reservar la última unidad disponible entre dos hilos simultáneos,
 *       donde una reserva resulta exitosa y la otra falla con {@link InsufficientAvailabilityException}.</li>
 *   <li>El contrato {@link ReservationDirectoryAdapter} para consulta de partícipes desde {@code billing}.</li>
 * </ol>
 */
@SpringJUnitConfig(classes = RentalConfirmedIntegrationEventTest.TestContextConfig.class)
class RentalConfirmedIntegrationEventTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID PROVIDER_ID = Ids.newId();
    private static final UUID CLIENT_1_ID = Ids.newId();
    private static final UUID CLIENT_2_ID = Ids.newId();
    private static final UUID TOOL_ID = Ids.newId();
    private static final UUID UNIT_ID = Ids.newId();

    @Configuration
    static class TestContextConfig {

        @Bean
        Clock clock() {
            return FIXED_CLOCK;
        }

        @Bean
        InMemoryReservationStore reservationStore() {
            return new InMemoryReservationStore();
        }

        @Bean
        ToolCatalogPort toolCatalogPort() {
            return toolId -> {
                if (TOOL_ID.equals(toolId)) {
                    return Optional.of(new ToolCatalogPort.RentableToolDescriptor(
                            TOOL_ID,
                            PROVIDER_ID,
                            "Rotomartillo SDS Plus",
                            true,
                            RateCard.of(Money.of("50.00"), Money.of("300.00"), Money.of("1000.00"), Money.of("120.00"))
                    ));
                }
                return Optional.empty();
            };
        }

        @Bean
        UnitInventoryPort unitInventoryPort(InMemoryReservationStore store) {
            return new UnitInventoryPort() {
                @Override
                public List<OperationalUnitDescriptor> lockOperationalUnits(UUID toolId) {
                    store.acquireToolLock(toolId);
                    if (TOOL_ID.equals(toolId)) {
                        return List.of(new OperationalUnitDescriptor(UNIT_ID, TOOL_ID, "SDS-SN-001"));
                    }
                    return List.of();
                }

                @Override
                public Optional<OperationalUnitDescriptor> findUnit(UUID unitId) {
                    if (UNIT_ID.equals(unitId)) {
                        return Optional.of(new OperationalUnitDescriptor(UNIT_ID, TOOL_ID, "SDS-SN-001"));
                    }
                    return Optional.empty();
                }

                @Override
                public void sendToMaintenance(UUID unitId, String reason) {
                }

                @Override
                public void retire(UUID unitId, String reason) {
                }
            };
        }

        @Bean
        UserVerificationPort userVerificationPort() {
            return new UserVerificationPort() {
                @Override
                public boolean isActiveClient(UUID userId) {
                    return true;
                }

                @Override
                public boolean isApprovedProvider(UUID userId) {
                    return true;
                }
            };
        }

        @Bean
        PricingPolicy pricingPolicy() {
            return new PricingPolicy();
        }

        @Bean
        UnitAllocator unitAllocator() {
            return new UnitAllocator();
        }

        @Bean
        SpringIntegrationEventPublisher integrationEventPublisher(org.springframework.context.ApplicationEventPublisher publisher) {
            return new SpringIntegrationEventPublisher(publisher);
        }

        @Bean
        ReservationBookingService reservationBookingService(
                InMemoryReservationStore store,
                ToolCatalogPort toolCatalogPort,
                UnitInventoryPort unitInventoryPort,
                UserVerificationPort userVerificationPort,
                PricingPolicy pricingPolicy,
                UnitAllocator unitAllocator,
                Clock clock
        ) {
            return new ReservationBookingService(
                    store,
                    store,
                    toolCatalogPort,
                    unitInventoryPort,
                    userVerificationPort,
                    pricingPolicy,
                    unitAllocator,
                    clock
            );
        }

        @Bean
        ReservationLifecycleService reservationLifecycleService(
                InMemoryReservationStore store,
                UnitInventoryPort unitInventoryPort,
                ToolCatalogPort toolCatalogPort,
                SpringIntegrationEventPublisher integrationEventPublisher,
                Clock clock
        ) {
            return new ReservationLifecycleService(
                    store,
                    unitInventoryPort,
                    toolCatalogPort,
                    integrationEventPublisher,
                    clock
            );
        }

        @Bean
        ReservationQueryService reservationQueryService(
                InMemoryReservationStore store,
                UnitInventoryPort unitInventoryPort,
                ToolCatalogPort toolCatalogPort
        ) {
            return new ReservationQueryService(store, unitInventoryPort, toolCatalogPort);
        }

        @Bean
        ReservationDirectoryAdapter reservationDirectoryAdapter(InMemoryReservationStore store) {
            return new ReservationDirectoryAdapter(store);
        }

        @Bean
        CapturedRentalEventsListener capturedRentalEventsListener() {
            return new CapturedRentalEventsListener();
        }
    }

    static class CapturedRentalEventsListener {
        private final List<ReservationConfirmed> confirmedEvents = new CopyOnWriteArrayList<>();
        private final List<ReservationCancelled> cancelledEvents = new CopyOnWriteArrayList<>();

        @EventListener
        public void onConfirmed(ReservationConfirmed event) {
            confirmedEvents.add(event);
        }

        @EventListener
        public void onCancelled(ReservationCancelled event) {
            cancelledEvents.add(event);
        }

        void clear() {
            confirmedEvents.clear();
            cancelledEvents.clear();
        }
    }

    static class InMemoryReservationStore implements ReservationRepository, UnitAvailabilityPort {
        private final Map<UUID, Reservation> byId = new ConcurrentHashMap<>();
        private final ReentrantLock toolLock = new ReentrantLock(true);

        void acquireToolLock(UUID toolId) {
            toolLock.lock();
        }

        void releaseToolLockIfHeld() {
            if (toolLock.isHeldByCurrentThread()) {
                toolLock.unlock();
            }
        }

        void clear() {
            byId.clear();
        }

        @Override
        public Reservation save(Reservation reservation) {
            byId.put(reservation.id().value(), reservation);
            return reservation;
        }

        @Override
        public Optional<Reservation> findById(ReservationId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public Optional<Reservation> findByCode(ReservationCode code) {
            return byId.values().stream()
                    .filter(r -> r.code().equals(code))
                    .findFirst();
        }

        @Override
        public boolean existsByCode(ReservationCode code) {
            return findByCode(code).isPresent();
        }

        @Override
        public PageResult<Reservation> findByClient(ClientId clientId, ReservationStatus status, PageQuery pageQuery) {
            List<Reservation> items = byId.values().stream()
                    .filter(r -> r.clientId().equals(clientId))
                    .filter(r -> status == null || r.status() == status)
                    .toList();
            return PageResult.of(items, pageQuery.page(), pageQuery.size(), items.size());
        }

        @Override
        public PageResult<Reservation> findByProvider(ProviderId providerId, ReservationStatus status, PageQuery pageQuery) {
            List<Reservation> items = byId.values().stream()
                    .filter(r -> r.providerId().equals(providerId))
                    .filter(r -> status == null || r.status() == status)
                    .toList();
            return PageResult.of(items, pageQuery.page(), pageQuery.size(), items.size());
        }

        @Override
        public PageResult<Reservation> findAll(ReservationStatus status, PageQuery pageQuery) {
            List<Reservation> items = byId.values().stream()
                    .filter(r -> status == null || r.status() == status)
                    .toList();
            return PageResult.of(items, pageQuery.page(), pageQuery.size(), items.size());
        }

        @Override
        public Set<UnitId> findOccupiedUnits(Collection<UnitId> candidateUnitIds, DateRange dateRange) {
            Set<UnitId> candidates = Set.copyOf(candidateUnitIds);
            Set<UnitId> occupied = new LinkedHashSet<>();
            for (Reservation r : byId.values()) {
                if (r.status().occupiesUnitsOnOverlap() && r.dateRange().overlaps(dateRange)) {
                    for (ReservationUnit ru : r.units()) {
                        if (candidates.contains(ru.unitId())) {
                            occupied.add(ru.unitId());
                        }
                    }
                }
            }
            return Set.copyOf(occupied);
        }
    }

    @Autowired
    private ReservationBookingService bookingService;

    @Autowired
    private ReservationLifecycleService lifecycleService;

    @Autowired
    private ReservationDirectoryAdapter reservationDirectoryAdapter;

    @Autowired
    private CapturedRentalEventsListener eventListener;

    @Autowired
    private InMemoryReservationStore store;

    @BeforeEach
    void setUp() {
        store.clear();
        eventListener.clear();
    }

    @Test
    @DisplayName("Cotiza, crea, confirma y publica ReservationConfirmed con el payload completo para Billing")
    void confirmsReservationAndPublishesReservationConfirmedEventWithExpectedPayload() {
        Actor clientActor = new Actor(CLIENT_1_ID, Set.of(ActorRole.CLIENTE));
        Actor providerActor = new Actor(PROVIDER_ID, Set.of(ActorRole.PROVEEDOR));
        DateRange dateRange = DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22)); // 3 días

        Quote quote = bookingService.quoteReservation(
                clientActor,
                new QuoteReservationCommand(dateRange, List.of(new RequestedToolItem(TOOL_ID, 1)))
        );
        assertThat(quote.days()).isEqualTo(3L);
        assertThat(quote.totalAgreedAmount()).isEqualTo(Money.of("150.00"));
        assertThat(quote.totalDepositAmount()).isEqualTo(Money.of("120.00"));

        ReservationView created;
        try {
            created = bookingService.createReservation(
                    clientActor,
                    new CreateReservationCommand(dateRange, List.of(new RequestedToolItem(TOOL_ID, 1)))
            );
        } finally {
            store.releaseToolLockIfHeld();
        }

        assertThat(created.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(created.code()).hasSize(10);

        // Verificar ReservationDirectoryAdapter (Hito M2)
        Optional<ReservationParticipants> participants = reservationDirectoryAdapter.findParticipants(created.id());
        assertThat(participants).isPresent();
        assertThat(participants.get().clientId()).isEqualTo(CLIENT_1_ID);
        assertThat(participants.get().providerId()).isEqualTo(PROVIDER_ID);
        assertThat(participants.get().code()).isEqualTo(created.code());

        // Confirmar reserva como proveedor dueño
        ReservationView confirmed = lifecycleService.confirmReservation(providerActor, created.id());
        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);

        // Verificar el evento de integración ReservationConfirmed capturado por el listener
        assertThat(eventListener.confirmedEvents).hasSize(1);
        ReservationConfirmed evt = eventListener.confirmedEvents.get(0);
        assertThat(evt.reservationId()).isEqualTo(created.id());
        assertThat(evt.reservationCode()).isEqualTo(created.code());
        assertThat(evt.clientId()).isEqualTo(CLIENT_1_ID);
        assertThat(evt.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(evt.confirmedBy()).isEqualTo(PROVIDER_ID);
        assertThat(evt.startDate()).isEqualTo(dateRange.start());
        assertThat(evt.endDate()).isEqualTo(dateRange.end());
        assertThat(evt.lines()).hasSize(1);

        ReservationConfirmed.Line line = evt.lines().get(0);
        assertThat(line.reservationUnitId()).isEqualTo(created.units().get(0).id());
        assertThat(line.toolName()).isEqualTo("Rotomartillo SDS Plus");
        assertThat(line.serialNumber()).isEqualTo("SDS-SN-001");
        assertThat(line.agreedAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(line.depositAmount()).isEqualByComparingTo(new BigDecimal("120.00"));

        // Cancelar la reserva confirmada como cliente y verificar ReservationCancelled
        ReservationView cancelled = lifecycleService.cancelReservation(clientActor, created.id(), "Ya no requiero el equipo");
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(eventListener.cancelledEvents).hasSize(1);
        ReservationCancelled cancelEvt = eventListener.cancelledEvents.get(0);
        assertThat(cancelEvt.reservationId()).isEqualTo(created.id());
        assertThat(cancelEvt.previousStatus()).isEqualTo("CONFIRMED");
        assertThat(cancelEvt.cancelledBy()).isEqualTo(CLIENT_1_ID);
        assertThat(cancelEvt.reason()).isEqualTo("Ya no requiero el equipo");
    }

    @Test
    @DisplayName("Dos hilos simultáneos reservando la única unidad disponible: una reserva tiene éxito y la otra falla con InsufficientAvailabilityException")
    void twoConcurrentThreadsBookingSingleUnitResultsInOneSuccessAndOneInsufficientAvailability() throws Exception {
        DateRange range = DateRange.of(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 27));
        CreateReservationCommand command = new CreateReservationCommand(
                range,
                List.of(new RequestedToolItem(TOOL_ID, 1))
        );

        Actor actor1 = new Actor(CLIENT_1_ID, Set.of(ActorRole.CLIENTE));
        Actor actor2 = new Actor(CLIENT_2_ID, Set.of(ActorRole.CLIENTE));

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<ReservationView> call1 = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                try {
                    return bookingService.createReservation(actor1, command);
                } finally {
                    store.releaseToolLockIfHeld();
                }
            };
            Callable<ReservationView> call2 = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                try {
                    return bookingService.createReservation(actor2, command);
                } finally {
                    store.releaseToolLockIfHeld();
                }
            };

            Future<ReservationView> f1 = executor.submit(call1);
            Future<ReservationView> f2 = executor.submit(call2);

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<ReservationView> successes = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();

            for (Future<ReservationView> f : List.of(f1, f2)) {
                try {
                    successes.add(f.get(5, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(InsufficientAvailabilityException.class);
        } finally {
            executor.shutdownNow();
        }
    }
}
