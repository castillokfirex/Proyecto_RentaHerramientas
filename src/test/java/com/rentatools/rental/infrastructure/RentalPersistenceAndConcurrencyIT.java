package com.rentatools.rental.infrastructure;

import com.rentatools.inventory.infrastructure.adapter.in.api.ToolCatalogApiAdapter;
import com.rentatools.inventory.infrastructure.adapter.in.api.UnitInventoryApiAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolUnitPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.rental.api.ReservationDirectory;
import com.rentatools.rental.api.ReservationParticipants;
import com.rentatools.rental.application.dto.CreateReservationCommand;
import com.rentatools.rental.application.dto.RequestedToolItem;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitAvailabilityPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.application.service.ReservationBookingService;
import com.rentatools.rental.domain.exception.InsufficientAvailabilityException;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.rental.domain.service.PricingPolicy;
import com.rentatools.rental.domain.service.UnitAllocator;
import com.rentatools.rental.infrastructure.adapter.in.api.ReservationDirectoryAdapter;
import com.rentatools.rental.infrastructure.adapter.out.inventory.InventoryToolCatalogAdapter;
import com.rentatools.rental.infrastructure.adapter.out.inventory.InventoryUnitAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.ReservationPersistenceAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.UnitAvailabilityAdapter;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.support.AbstractMySqlIT;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de persistencia y concurrencia contra MySQL 8.4 (Testcontainers):
 * verifica que cuando dos hilos simultáneos intentan reservar la única unidad disponible en el mismo
 * rango de fechas, {@code lockOperationalUnits} ({@code SELECT ... FOR UPDATE}) serializa la ejecución
 * de modo que exactamente una reserva tiene éxito y la otra falla con {@link InsufficientAvailabilityException}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ReservationPersistenceAdapter.class,
        UnitAvailabilityAdapter.class,
        ReservationDirectoryAdapter.class,
        InventoryToolCatalogAdapter.class,
        InventoryUnitAdapter.class,
        ToolPersistenceAdapter.class,
        ToolUnitPersistenceAdapter.class,
        ToolCatalogApiAdapter.class,
        UnitInventoryApiAdapter.class,
        InventoryPersistenceMapper.class,
        RentalPersistenceAndConcurrencyIT.TestConfig.class
})
class RentalPersistenceAndConcurrencyIT extends AbstractMySqlIT {

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UnitAvailabilityPort unitAvailabilityPort;

    @Autowired
    private ToolCatalogPort toolCatalogPort;

    @Autowired
    private UnitInventoryPort unitInventoryPort;

    @Autowired
    private ReservationDirectory reservationDirectory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Dos hilos simultáneos reservan la última unidad disponible: solo una reserva se crea y la otra lanza InsufficientAvailabilityException")
    void concurrentBookingOfSingleAvailableUnitSucceedsOnceAndFailsWithInsufficientAvailability() throws Exception {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        UUID providerId = Ids.newId();
        UUID client1Id = Ids.newId();
        UUID client2Id = Ids.newId();
        UUID toolId = Ids.newId();
        UUID unitId = Ids.newId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        txTemplate.executeWithoutResult(status -> {
            seedUser(providerId, "prov.conc@rentatools.com", "Proveedor Concurrencia", now);
            seedUser(client1Id, "cli1.conc@rentatools.com", "Cliente Uno", now);
            seedUser(client2Id, "cli2.conc@rentatools.com", "Cliente Dos", now);
            seedActiveToolWithSingleUnit(toolId, providerId, unitId, "SN-UNICA-001", now);
        });

        ReservationBookingService bookingService = new ReservationBookingService(
                reservationRepository,
                unitAvailabilityPort,
                toolCatalogPort,
                unitInventoryPort,
                new com.rentatools.rental.application.port.out.UserVerificationPort() {
                    @Override
                    public boolean isActiveClient(UUID userId) {
                        return true;
                    }

                    @Override
                    public boolean isApprovedProvider(UUID userId) {
                        return true;
                    }
                },
                new PricingPolicy(),
                new UnitAllocator(),
                clock
        );

        DateRange range = DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
        CreateReservationCommand command = new CreateReservationCommand(
                range,
                List.of(new RequestedToolItem(toolId, 1))
        );

        Actor actor1 = new Actor(client1Id, Set.of(ActorRole.CLIENTE));
        Actor actor2 = new Actor(client2Id, Set.of(ActorRole.CLIENTE));

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<ReservationView> task1 = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                return txTemplate.execute(status -> bookingService.createReservation(actor1, command));
            };
            Callable<ReservationView> task2 = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                return txTemplate.execute(status -> bookingService.createReservation(actor2, command));
            };

            Future<ReservationView> f1 = executor.submit(task1);
            Future<ReservationView> f2 = executor.submit(task2);

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<ReservationView> successes = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();

            for (Future<ReservationView> f : List.of(f1, f2)) {
                try {
                    successes.add(f.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(InsufficientAvailabilityException.class);

            ReservationView created = successes.get(0);
            assertThat(created.status()).isEqualTo(ReservationStatus.PENDING);
            assertThat(created.units()).hasSize(1);
            assertThat(created.units().get(0).unitId()).isEqualTo(unitId);

            // Verificar que UnitAvailabilityAdapter reporta la unidad como ocupada en rango solapado
            Set<UnitId> occupied = txTemplate.execute(status ->
                    unitAvailabilityPort.findOccupiedUnits(List.of(UnitId.of(unitId)), range)
            );
            assertThat(occupied).containsExactly(UnitId.of(unitId));

            // Verificar que ReservationDirectoryAdapter expone los partícipes de la reserva
            Optional<ReservationParticipants> participants = txTemplate.execute(status ->
                    reservationDirectory.findParticipants(created.id())
            );
            assertThat(participants).isPresent();
            assertThat(participants.get().providerId()).isEqualTo(providerId);
        } finally {
            executor.shutdownNow();
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM reservation_units WHERE unit_id = ?", (Object) toBytes(unitId));
                jdbcTemplate.update("DELETE FROM reservations WHERE provider_id = ?", (Object) toBytes(providerId));
                jdbcTemplate.update("DELETE FROM tool_units WHERE id = ?", (Object) toBytes(unitId));
                jdbcTemplate.update("DELETE FROM tool_rates WHERE tool_id = ?", (Object) toBytes(toolId));
                jdbcTemplate.update("DELETE FROM tools WHERE id = ?", (Object) toBytes(toolId));
                jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)",
                        toBytes(providerId), toBytes(client1Id), toBytes(client2Id));
            });
        }
    }

    private void seedUser(UUID id, String email, String fullName, Instant now) {
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at, version)
                VALUES (?, ?, '$2a$10$hash', ?, 'ACTIVE', ?, ?, 0)
                """,
                toBytes(id),
                email,
                fullName,
                ts,
                ts
        );
    }

    private void seedActiveToolWithSingleUnit(
            UUID toolId,
            UUID providerId,
            UUID unitId,
            String serialNumber,
            Instant now
    ) {
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO tools (id, provider_id, name, brand, model, description, deposit_amount, status, created_at, updated_at, version)
                VALUES (?, ?, 'Taladro Concurrencia', 'Bosch', 'GBH 2-28', 'Taladro percutor', 100.00, 'ACTIVE', ?, ?, 0)
                """,
                toBytes(toolId),
                toBytes(providerId),
                ts,
                ts
        );
        jdbcTemplate.update(
                """
                INSERT INTO tool_rates (tool_id, period, price)
                VALUES (?, 'DAY', 50.00)
                """,
                toBytes(toolId)
        );
        jdbcTemplate.update(
                """
                INSERT INTO tool_units (id, tool_id, serial_number, status, created_at, version)
                VALUES (?, ?, ?, 'AVAILABLE', ?, 0)
                """,
                toBytes(unitId),
                toBytes(toolId),
                serialNumber,
                ts
        );
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
