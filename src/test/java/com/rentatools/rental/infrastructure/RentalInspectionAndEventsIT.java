package com.rentatools.rental.infrastructure;

import com.rentatools.inventory.infrastructure.adapter.in.api.ToolCatalogApiAdapter;
import com.rentatools.inventory.infrastructure.adapter.in.api.UnitInventoryApiAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolUnitPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.rental.application.dto.AttachInspectionPhotoCommand;
import com.rentatools.rental.application.dto.ChecklistItemView;
import com.rentatools.rental.application.dto.CreateChecklistItemCommand;
import com.rentatools.rental.application.dto.FrequentClientReportView;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.application.dto.RegisterInspectionCommand;
import com.rentatools.rental.application.dto.ToolStatusReportView;
import com.rentatools.rental.application.dto.TopToolReportView;
import com.rentatools.rental.application.dto.UpdateChecklistItemCommand;
import com.rentatools.rental.application.port.out.ChecklistItemRepository;
import com.rentatools.rental.application.port.out.InspectionRepository;
import com.rentatools.rental.application.port.out.RentalReportPort;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.application.service.ChecklistService;
import com.rentatools.rental.application.service.InspectionService;
import com.rentatools.rental.application.service.RentalReportService;
import com.rentatools.rental.application.service.ReservationLifecycleService;
import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.inspection.ResultValue;
import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.rental.infrastructure.adapter.in.event.BillingEventsListener;
import com.rentatools.rental.infrastructure.adapter.out.inventory.InventoryToolCatalogAdapter;
import com.rentatools.rental.infrastructure.adapter.out.inventory.InventoryUnitAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.ChecklistPersistenceAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.InspectionPersistenceAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.RentalReportPersistenceAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.ReservationPersistenceAdapter;
import com.rentatools.rental.infrastructure.adapter.out.persistence.UnitAvailabilityAdapter;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.infrastructure.storage.LocalFileStorage;
import com.rentatools.shared.infrastructure.storage.StorageProperties;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import com.rentatools.shared.integration.rental.ReservationReturned;
import com.rentatools.support.AbstractMySqlIT;
import com.rentatools.support.fakes.InMemoryIntegrationEventPublisher;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ReservationPersistenceAdapter.class,
        UnitAvailabilityAdapter.class,
        InventoryToolCatalogAdapter.class,
        InventoryUnitAdapter.class,
        ToolPersistenceAdapter.class,
        ToolUnitPersistenceAdapter.class,
        ToolCatalogApiAdapter.class,
        UnitInventoryApiAdapter.class,
        InventoryPersistenceMapper.class,
        InspectionPersistenceAdapter.class,
        ChecklistPersistenceAdapter.class,
        RentalReportPersistenceAdapter.class,
        RentalInspectionAndEventsIT.TestConfig.class
})
class RentalInspectionAndEventsIT extends AbstractMySqlIT {

    @TempDir
    static Path tempDir;

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        InMemoryIntegrationEventPublisher inMemoryIntegrationEventPublisher() {
            return new InMemoryIntegrationEventPublisher();
        }

        @Bean
        FileStorage localFileStorage() {
            return new LocalFileStorage(new StorageProperties(tempDir.toString()));
        }
    }

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private ChecklistItemRepository checklistItemRepository;

    @Autowired
    private RentalReportPort rentalReportPort;

    @Autowired
    private ToolCatalogPort toolCatalogPort;

    @Autowired
    private UnitInventoryPort unitInventoryPort;

    @Autowired
    private InMemoryIntegrationEventPublisher eventPublisher;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private Clock testClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private InspectionService inspectionService;
    private ChecklistService checklistService;
    private ReservationLifecycleService lifecycleService;
    private RentalReportService rentalReportService;
    private BillingEventsListener billingEventsListener;

    private UUID clientId;
    private UUID providerId;
    private UUID toolId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        eventPublisher.clear();

        inspectionService = new InspectionService(
                reservationRepository,
                inspectionRepository,
                checklistItemRepository,
                unitInventoryPort,
                toolCatalogPort,
                eventPublisher,
                fileStorage,
                testClock
        );

        checklistService = new ChecklistService(checklistItemRepository);

        lifecycleService = new ReservationLifecycleService(
                reservationRepository,
                unitInventoryPort,
                toolCatalogPort,
                eventPublisher,
                testClock
        );

        rentalReportService = new RentalReportService(rentalReportPort);

        billingEventsListener = new BillingEventsListener(
                lifecycleService,
                lifecycleService
        );

        clientId = Ids.newId();
        providerId = Ids.newId();
        toolId = Ids.newId();
        unitId = Ids.newId();

        insertUser(clientId, "Cliente Frecuente", "cliente.frecuente@test.com", "CLIENTE");
        insertUser(providerId, "Proveedor Pro", "proveedor.pro@test.com", "PROVEEDOR");
        insertProviderProfile(providerId);

        insertTool(toolId, providerId, "Taladro Percutor Bosch", "Bosch", "GSB 18V-50");
        insertToolUnit(unitId, toolId, "SN-BOSCH-901", "AVAILABLE");
    }

    @Test
    @DisplayName("Flujo completo de inspecciones: DELIVERY -> ACTIVE -> RETURN -> RETURNED, eventos de facturación y reportes")
    void shouldExecuteFullInspectionLifecycleAndBillingEvents() {
        // 1. Crear una reserva en estado CONFIRMED
        ReservationId resId = ReservationId.of(Ids.newId());
        ReservationUnitId ruId = ReservationUnitId.of(Ids.newId());
        LocalDate startDate = LocalDate.of(2026, 10, 18);
        LocalDate endDate = LocalDate.of(2026, 10, 20); // 3 días

        Reservation reservation = Reservation.reconstruct(
                resId,
                ReservationCode.generate(),
                ClientId.of(clientId),
                ProviderId.of(providerId),
                DateRange.of(startDate, endDate),
                ReservationStatus.CONFIRMED,
                null,
                List.of(ReservationUnit.reconstruct(
                        ruId,
                        UnitId.of(unitId),
                        Money.of("50.00"),
                        Money.of("150.00"),
                        Money.of("100.00")
                )),
                testClock.instant(),
                testClock.instant(),
                0L
        );
        reservationRepository.save(reservation);

        // 2. Billing emite ReservationClearedForDelivery -> Listener pasa a READY_FOR_PICKUP (con idempotencia)
        billingEventsListener.onReservationClearedForDelivery(new ReservationClearedForDelivery(
                Ids.newId(),
                testClock.instant(),
                resId.value()
        ));
        Reservation readyRes = reservationRepository.findById(resId).orElseThrow();
        assertThat(readyRes.status()).isEqualTo(ReservationStatus.READY_FOR_PICKUP);

        // Reintento idempotente
        billingEventsListener.onReservationClearedForDelivery(new ReservationClearedForDelivery(
                Ids.newId(),
                testClock.instant(),
                resId.value()
        ));
        assertThat(reservationRepository.findById(resId).orElseThrow().status())
                .isEqualTo(ReservationStatus.READY_FOR_PICKUP);

        // 3. Registrar inspección DELIVERY -> debe pasar a ACTIVE
        Actor providerActor = new Actor(providerId, Set.of(ActorRole.PROVEEDOR));

        RegisterInspectionCommand deliveryCommand = new RegisterInspectionCommand(
                resId.value(),
                ruId.value(),
                InspectionType.DELIVERY,
                OverallCondition.GOOD,
                "Entrega en perfecto estado con accesorios completos",
                List.of(
                        new RegisterInspectionCommand.ChecklistResultItem(1, ResultValue.OK, "Carcasa intacta"),
                        new RegisterInspectionCommand.ChecklistResultItem(2, ResultValue.OK, "Cable en buen estado")
                )
        );

        InspectionView deliveryView = inspectionService.registerInspection(providerActor, deliveryCommand);
        assertThat(deliveryView.type()).isEqualTo(InspectionType.DELIVERY);
        assertThat(deliveryView.overallCondition()).isEqualTo(OverallCondition.GOOD);

        Reservation activeRes = reservationRepository.findById(resId).orElseThrow();
        assertThat(activeRes.status()).isEqualTo(ReservationStatus.ACTIVE);

        // 4. Adjuntar fotografía de evidencia
        byte[] fakePhotoBytes = "fake-photo-content-jpg".getBytes(StandardCharsets.UTF_8);
        AttachInspectionPhotoCommand photoCommand = new AttachInspectionPhotoCommand(
                deliveryView.id(),
                "foto_entrega.jpg",
                "image/jpeg",
                fakePhotoBytes.length,
                new ByteArrayInputStream(fakePhotoBytes)
        );

        InspectionPhotoView photoView = inspectionService.attachPhoto(providerActor, photoCommand);
        assertThat(photoView.inspectionId()).isEqualTo(deliveryView.id());
        assertThat(photoView.contentType()).isEqualTo("image/jpeg");

        // 5. Registrar inspección RETURN con 2 días de retraso y avería grave (MAJOR_DAMAGE)
        // El reloj de devolución simula fecha 2026-10-22 (fin pactado 2026-10-20 -> lateDays = 2)
        Clock returnClock = Clock.fixed(Instant.parse("2026-10-22T14:30:00Z"), ZoneOffset.UTC);
        InspectionService returnInspectionService = new InspectionService(
                reservationRepository,
                inspectionRepository,
                checklistItemRepository,
                unitInventoryPort,
                toolCatalogPort,
                eventPublisher,
                fileStorage,
                returnClock
        );

        RegisterInspectionCommand returnCommand = new RegisterInspectionCommand(
                resId.value(),
                ruId.value(),
                InspectionType.RETURN,
                OverallCondition.MAJOR_DAMAGE,
                "Motor quemado por sobreesfuerzo",
                List.of(
                        new RegisterInspectionCommand.ChecklistResultItem(1, ResultValue.DAMAGED, "Golpe severo"),
                        new RegisterInspectionCommand.ChecklistResultItem(3, ResultValue.DAMAGED, "No enciende")
                )
        );

        InspectionView returnView = returnInspectionService.registerInspection(providerActor, returnCommand);
        assertThat(returnView.type()).isEqualTo(InspectionType.RETURN);
        assertThat(returnView.overallCondition()).isEqualTo(OverallCondition.MAJOR_DAMAGE);

        // La reserva debe haber pasado a RETURNED
        Reservation returnedRes = reservationRepository.findById(resId).orElseThrow();
        assertThat(returnedRes.status()).isEqualTo(ReservationStatus.RETURNED);

        // La unidad física debe haber sido enviada a MAINTENANCE automáticamente
        String unitStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM tool_units WHERE id = ?",
                String.class,
                toBytes(unitId)
        );
        assertThat(unitStatus).isEqualTo("MAINTENANCE");

        // Verificar que se haya publicado el evento de integración ReservationReturned con el detalle esperado
        List<ReservationReturned> returnedEvents = eventPublisher.eventsOfType(ReservationReturned.class);
        assertThat(returnedEvents).hasSize(1);
        ReservationReturned event = returnedEvents.get(0);
        assertThat(event.reservationId()).isEqualTo(resId.value());
        assertThat(event.registeredBy()).isEqualTo(providerId);
        assertThat(event.units()).hasSize(1);

        ReservationReturned.ReturnedUnit returnedUnit = event.units().get(0);
        assertThat(returnedUnit.reservationUnitId()).isEqualTo(ruId.value());
        assertThat(returnedUnit.lateDays()).isEqualTo(2); // 2026-10-22 - 2026-10-20
        assertThat(returnedUnit.dailyRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(returnedUnit.damageReported()).isTrue();
        assertThat(returnedUnit.lost()).isFalse();

        // 6. Billing emite ReservationSettled -> Listener pasa la reserva a CLOSED (con idempotencia)
        billingEventsListener.onReservationSettled(new ReservationSettled(
                Ids.newId(),
                returnClock.instant(),
                resId.value(),
                BigDecimal.ZERO,
                new BigDecimal("100.00")
        ));
        Reservation closedRes = reservationRepository.findById(resId).orElseThrow();
        assertThat(closedRes.status()).isEqualTo(ReservationStatus.CLOSED);

        billingEventsListener.onReservationSettled(new ReservationSettled(
                Ids.newId(),
                returnClock.instant(),
                resId.value(),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));
        assertThat(reservationRepository.findById(resId).orElseThrow().status())
                .isEqualTo(ReservationStatus.CLOSED);

        // 7. Verificación de los Reportes de Administración exigidos por la rúbrica
        Actor adminActor = new Actor(Ids.newId(), Set.of(ActorRole.ADMIN));

        // Reporte 1: Top tools
        List<TopToolReportView> topTools = rentalReportService.getTopTools(adminActor, 10);
        assertThat(topTools).isNotEmpty();
        TopToolReportView topTool = topTools.stream()
                .filter(t -> t.toolId().equals(toolId))
                .findFirst()
                .orElseThrow();
        assertThat(topTool.name()).isEqualTo("Taladro Percutor Bosch");
        assertThat(topTool.totalRentals()).isGreaterThanOrEqualTo(1L);
        assertThat(topTool.totalRentalDays()).isGreaterThanOrEqualTo(3L);

        // Reporte 2: Clientes frecuentes
        List<FrequentClientReportView> frequentClients = rentalReportService.getFrequentClients(adminActor, 10);
        assertThat(frequentClients).isNotEmpty();
        FrequentClientReportView clientReport = frequentClients.stream()
                .filter(c -> c.clientId().equals(clientId))
                .findFirst()
                .orElseThrow();
        assertThat(clientReport.fullName()).isEqualTo("Cliente Frecuente");
        assertThat(clientReport.closedReservations()).isGreaterThanOrEqualTo(1L);

        // Reporte 3: Tool status summary
        ToolStatusReportView statusSummary = rentalReportService.getToolStatusSummary(adminActor);
        assertThat(statusSummary.totalUnits()).isGreaterThanOrEqualTo(1L);
        assertThat(statusSummary.maintenanceUnits()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Gestión de ítems de checklist: CRUD por parte del administrador")
    void shouldManageChecklistItems() {
        Actor adminActor = new Actor(Ids.newId(), Set.of(ActorRole.ADMIN));

        CreateChecklistItemCommand createCommand = new CreateChecklistItemCommand(
                "NIVEL_ACEITE",
                "Nivel y calidad de aceite de lubricación"
        );
        ChecklistItemView created = checklistService.createItem(adminActor, createCommand);
        assertThat(created.code()).isEqualTo("NIVEL_ACEITE");
        assertThat(created.active()).isTrue();

        UpdateChecklistItemCommand updateCommand = new UpdateChecklistItemCommand(
                "Nivel de aceite (inspección obligatoria)",
                false
        );
        ChecklistItemView updated = checklistService.updateItem(adminActor, created.id(), updateCommand);
        assertThat(updated.label()).isEqualTo("Nivel de aceite (inspección obligatoria)");
        assertThat(updated.active()).isFalse();

        List<ChecklistItemView> all = checklistService.listItems(false);
        assertThat(all).anyMatch(i -> i.id() == created.id());

        List<ChecklistItemView> activeOnly = checklistService.listItems(true);
        assertThat(activeOnly).noneMatch(i -> i.id() == created.id());
    }

    private void insertUser(UUID id, String fullName, String email, String role) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, full_name, active, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, TRUE, NOW(), NOW())
                ON DUPLICATE KEY UPDATE full_name = VALUES(full_name)
                """,
                toBytes(id), email, fullName
        );
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE role = VALUES(role)
                """,
                toBytes(id), role
        );
    }

    private void insertProviderProfile(UUID providerId) {
        jdbcTemplate.update("""
                INSERT INTO provider_profiles (user_id, company_name, tax_id, phone, address, approved_at, created_at, updated_at)
                VALUES (?, 'Herramientas SA', 'RUC12345678', '555-1234', 'Av. Central 123', NOW(), NOW(), NOW())
                ON DUPLICATE KEY UPDATE company_name = VALUES(company_name)
                """,
                toBytes(providerId)
        );
    }

    private void insertTool(UUID toolId, UUID providerId, String name, String brand, String model) {
        jdbcTemplate.update("""
                INSERT INTO tools (id, provider_id, name, brand, model, description, replacement_cost, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'Descripción', 300.00, 'ACTIVE', NOW(), NOW())
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """,
                toBytes(toolId), toBytes(providerId), name, brand, model
        );
        jdbcTemplate.update("""
                INSERT INTO tool_rates (tool_id, period, price)
                VALUES (?, 'DAY', 50.00)
                ON DUPLICATE KEY UPDATE price = VALUES(price)
                """,
                toBytes(toolId)
        );
    }

    private void insertToolUnit(UUID unitId, UUID toolId, String serialNumber, String status) {
        jdbcTemplate.update("""
                INSERT INTO tool_units (id, tool_id, serial_number, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE status = VALUES(status)
                """,
                toBytes(unitId), toBytes(toolId), serialNumber, status
        );
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
