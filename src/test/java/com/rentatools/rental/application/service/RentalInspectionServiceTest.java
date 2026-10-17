package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.AttachInspectionPhotoCommand;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.application.dto.RegisterInspectionCommand;
import com.rentatools.rental.application.port.out.ChecklistItemRepository;
import com.rentatools.rental.application.port.out.InspectionRepository;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.domain.exception.DeliveryNotRegisteredException;
import com.rentatools.rental.domain.exception.InspectionAlreadyRegisteredException;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionId;
import com.rentatools.rental.domain.model.inspection.InspectionPhoto;
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
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.application.port.out.StoredFile;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import com.rentatools.shared.integration.rental.ReservationReturned;
import com.rentatools.support.fakes.InMemoryIntegrationEventPublisher;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalInspectionServiceTest {

    private static final Clock CLOCK_OCT_18 = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock CLOCK_OCT_22 = Clock.fixed(Instant.parse("2026-10-22T14:00:00Z"), ZoneOffset.UTC);

    private InMemoryReservationRepo reservationRepo;
    private InMemoryInspectionRepo inspectionRepo;
    private InMemoryChecklistRepo checklistRepo;
    private InMemoryUnitInventory unitInventory;
    private InMemoryToolCatalog toolCatalog;
    private InMemoryIntegrationEventPublisher eventPublisher;
    private InMemoryFileStorage fileStorage;

    private InspectionService inspectionService;
    private ReservationLifecycleService lifecycleService;

    private UUID clientId;
    private UUID providerId;
    private UUID toolId;
    private UUID unitId;
    private ReservationUnitId resUnitId;
    private ReservationId resId;

    @BeforeEach
    void setUp() {
        reservationRepo = new InMemoryReservationRepo();
        inspectionRepo = new InMemoryInspectionRepo();
        checklistRepo = new InMemoryChecklistRepo();
        unitInventory = new InMemoryUnitInventory();
        toolCatalog = new InMemoryToolCatalog();
        eventPublisher = new InMemoryIntegrationEventPublisher();
        fileStorage = new InMemoryFileStorage();

        inspectionService = new InspectionService(
                reservationRepo,
                inspectionRepo,
                checklistRepo,
                unitInventory,
                toolCatalog,
                eventPublisher,
                fileStorage,
                CLOCK_OCT_18
        );

        lifecycleService = new ReservationLifecycleService(
                reservationRepo,
                unitInventory,
                toolCatalog,
                eventPublisher,
                CLOCK_OCT_18
        );

        clientId = Ids.newId();
        providerId = Ids.newId();
        toolId = Ids.newId();
        unitId = Ids.newId();
        resUnitId = ReservationUnitId.of(Ids.newId());
        resId = ReservationId.of(Ids.newId());

        toolCatalog.registerTool(toolId, "Sierra Circular Bosch");
        unitInventory.registerUnit(unitId, toolId, "SN-SC-100");
    }

    @Test
    @DisplayName("Debe completar el ciclo de entrega y devolución publicando ReservationReturned con días de mora")
    void shouldCompleteDeliveryAndReturnCycleWithLateFeeEvent() {
        // Reserva para 18 al 20 de octubre (3 días)
        Reservation reservation = Reservation.reconstruct(
                resId,
                ReservationCode.generate(),
                ClientId.of(clientId),
                ProviderId.of(providerId),
                DateRange.of(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20)),
                ReservationStatus.CONFIRMED,
                null,
                List.of(ReservationUnit.reconstruct(
                        resUnitId,
                        UnitId.of(unitId),
                        Money.of("45.00"),
                        Money.of("135.00"),
                        Money.of("90.00")
                )),
                CLOCK_OCT_18.instant(),
                CLOCK_OCT_18.instant(),
                0L
        );
        reservationRepo.save(reservation);

        // 1. Simular evento de facturación ReservationClearedForDelivery
        lifecycleService.markClearedForDelivery(resId.value());
        assertThat(reservationRepo.findById(resId).orElseThrow().status())
                .isEqualTo(ReservationStatus.READY_FOR_PICKUP);

        // 2. Registrar inspección DELIVERY
        Actor providerActor = new Actor(providerId, Set.of(ActorRole.PROVEEDOR));
        RegisterInspectionCommand deliveryCmd = new RegisterInspectionCommand(
                resId.value(),
                resUnitId.value(),
                InspectionType.DELIVERY,
                OverallCondition.GOOD,
                "Entrega en perfecto estado",
                List.of(new RegisterInspectionCommand.ChecklistResultItem(1, ResultValue.OK, "OK"))
        );

        InspectionView deliveryView = inspectionService.registerInspection(providerActor, deliveryCmd);
        assertThat(deliveryView.type()).isEqualTo(InspectionType.DELIVERY);
        assertThat(reservationRepo.findById(resId).orElseThrow().status())
                .isEqualTo(ReservationStatus.ACTIVE);

        // 3. Adjuntar foto
        byte[] photoData = "imagen-bytes".getBytes(StandardCharsets.UTF_8);
        AttachInspectionPhotoCommand photoCmd = new AttachInspectionPhotoCommand(
                deliveryView.id(),
                "foto.png",
                "image/png",
                photoData.length,
                new ByteArrayInputStream(photoData)
        );
        InspectionPhotoView photoView = inspectionService.attachPhoto(providerActor, photoCmd);
        assertThat(photoView.contentType()).isEqualTo("image/png");

        // 4. Registrar inspección RETURN el 22 de octubre (2 días tarde) con MAJOR_DAMAGE
        InspectionService oct22Service = new InspectionService(
                reservationRepo,
                inspectionRepo,
                checklistRepo,
                unitInventory,
                toolCatalog,
                eventPublisher,
                fileStorage,
                CLOCK_OCT_22
        );

        RegisterInspectionCommand returnCmd = new RegisterInspectionCommand(
                resId.value(),
                resUnitId.value(),
                InspectionType.RETURN,
                OverallCondition.MAJOR_DAMAGE,
                "Disco roto y eje torcido",
                List.of(new RegisterInspectionCommand.ChecklistResultItem(1, ResultValue.DAMAGED, "Avería"))
        );

        InspectionView returnView = oct22Service.registerInspection(providerActor, returnCmd);
        assertThat(returnView.type()).isEqualTo(InspectionType.RETURN);
        assertThat(reservationRepo.findById(resId).orElseThrow().status())
                .isEqualTo(ReservationStatus.RETURNED);

        // Verifica que la unidad fue enviada a mantenimiento
        assertThat(unitInventory.maintenanceUnits).contains(unitId);

        // Verifica el evento ReservationReturned
        List<ReservationReturned> events = eventPublisher.eventsOfType(ReservationReturned.class);
        assertThat(events).hasSize(1);
        ReservationReturned event = events.get(0);
        assertThat(event.reservationId()).isEqualTo(resId.value());
        assertThat(event.units()).hasSize(1);

        ReservationReturned.ReturnedUnit unit = event.units().get(0);
        assertThat(unit.lateDays()).isEqualTo(2); // 2026-10-22 - 2026-10-20
        assertThat(unit.dailyRate()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(unit.damageReported()).isTrue();
        assertThat(unit.lost()).isFalse();

        // 5. Simular evento de facturación ReservationSettled
        lifecycleService.closeReservation(resId.value());
        assertThat(reservationRepo.findById(resId).orElseThrow().status())
                .isEqualTo(ReservationStatus.CLOSED);
    }

    @Test
    @DisplayName("Debe rechazar entrega antes de la fecha de inicio de la reserva")
    void shouldRejectDeliveryBeforeStartDate() {
        Reservation reservation = Reservation.reconstruct(
                resId,
                ReservationCode.generate(),
                ClientId.of(clientId),
                ProviderId.of(providerId),
                DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 25)),
                ReservationStatus.READY_FOR_PICKUP,
                null,
                List.of(ReservationUnit.reconstruct(
                        resUnitId,
                        UnitId.of(unitId),
                        Money.of("45.00"),
                        Money.of("270.00"),
                        Money.of("90.00")
                )),
                CLOCK_OCT_18.instant(),
                CLOCK_OCT_18.instant(),
                0L
        );
        reservationRepo.save(reservation);

        Actor providerActor = new Actor(providerId, Set.of(ActorRole.PROVEEDOR));
        RegisterInspectionCommand cmd = new RegisterInspectionCommand(
                resId.value(),
                resUnitId.value(),
                InspectionType.DELIVERY,
                OverallCondition.GOOD,
                null,
                List.of()
        );

        assertThatThrownBy(() -> inspectionService.registerInspection(providerActor, cmd))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("DELIVERY_BEFORE_START_DATE");
    }

    @Test
    @DisplayName("Debe rechazar devolución sin entrega previa")
    void shouldRejectReturnWithoutDelivery() {
        Reservation reservation = Reservation.reconstruct(
                resId,
                ReservationCode.generate(),
                ClientId.of(clientId),
                ProviderId.of(providerId),
                DateRange.of(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20)),
                ReservationStatus.ACTIVE,
                null,
                List.of(ReservationUnit.reconstruct(
                        resUnitId,
                        UnitId.of(unitId),
                        Money.of("45.00"),
                        Money.of("135.00"),
                        Money.of("90.00")
                )),
                CLOCK_OCT_18.instant(),
                CLOCK_OCT_18.instant(),
                0L
        );
        reservationRepo.save(reservation);

        Actor providerActor = new Actor(providerId, Set.of(ActorRole.PROVEEDOR));
        RegisterInspectionCommand returnCmd = new RegisterInspectionCommand(
                resId.value(),
                resUnitId.value(),
                InspectionType.RETURN,
                OverallCondition.GOOD,
                null,
                List.of()
        );

        assertThatThrownBy(() -> inspectionService.registerInspection(providerActor, returnCmd))
                .isInstanceOf(DeliveryNotRegisteredException.class);
    }

    // --- Fakes en memoria ---

    static class InMemoryReservationRepo implements ReservationRepository {
        private final Map<UUID, Reservation> store = new HashMap<>();

        @Override
        public Reservation save(Reservation reservation) {
            store.put(reservation.id().value(), reservation);
            return reservation;
        }

        @Override
        public Optional<Reservation> findById(ReservationId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<Reservation> findByCode(ReservationCode code) {
            return store.values().stream().filter(r -> r.code().equals(code)).findFirst();
        }

        @Override
        public boolean existsByCode(ReservationCode code) {
            return store.values().stream().anyMatch(r -> r.code().equals(code));
        }

        @Override
        public PageResult<Reservation> findByClient(ClientId clientId, ReservationStatus status, PageQuery pageQuery) {
            return PageResult.of(List.of(), 0, 10, 0);
        }

        @Override
        public PageResult<Reservation> findByProvider(ProviderId providerId, ReservationStatus status, PageQuery pageQuery) {
            return PageResult.of(List.of(), 0, 10, 0);
        }

        @Override
        public PageResult<Reservation> findAll(ReservationStatus status, PageQuery pageQuery) {
            return PageResult.of(List.of(), 0, 10, 0);
        }

        @Override
        public Optional<Reservation> findByReservationUnitId(ReservationUnitId reservationUnitId) {
            return store.values().stream()
                    .filter(r -> r.findReservationUnit(reservationUnitId).isPresent())
                    .findFirst();
        }
    }

    static class InMemoryInspectionRepo implements InspectionRepository {
        private final Map<UUID, Inspection> store = new HashMap<>();

        @Override
        public Inspection save(Inspection inspection) {
            store.put(inspection.id().value(), inspection);
            return inspection;
        }

        @Override
        public Optional<Inspection> findById(InspectionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<Inspection> findByReservationUnitId(ReservationUnitId reservationUnitId) {
            return store.values().stream()
                    .filter(i -> i.reservationUnitId().equals(reservationUnitId))
                    .toList();
        }

        @Override
        public List<Inspection> findByReservationId(ReservationId reservationId) {
            return new ArrayList<>(store.values());
        }

        @Override
        public Optional<InspectionPhoto> findPhotoById(UUID photoId) {
            return store.values().stream()
                    .flatMap(i -> i.photos().stream())
                    .filter(p -> p.id().equals(photoId))
                    .findFirst();
        }
    }

    static class InMemoryChecklistRepo implements ChecklistItemRepository {
        @Override
        public ChecklistItem save(ChecklistItem item) {
            return item;
        }

        @Override
        public Optional<ChecklistItem> findById(ChecklistItemId id) {
            return Optional.empty();
        }

        @Override
        public Optional<ChecklistItem> findByCode(String code) {
            return Optional.empty();
        }

        @Override
        public List<ChecklistItem> findAll() {
            return List.of();
        }

        @Override
        public List<ChecklistItem> findAllActive() {
            return List.of();
        }
    }

    static class InMemoryUnitInventory implements UnitInventoryPort {
        private final Map<UUID, OperationalUnitDescriptor> units = new HashMap<>();
        final List<UUID> maintenanceUnits = new ArrayList<>();
        final List<UUID> retiredUnits = new ArrayList<>();

        void registerUnit(UUID unitId, UUID toolId, String sn) {
            units.put(unitId, new OperationalUnitDescriptor(unitId, toolId, sn));
        }

        @Override
        public List<OperationalUnitDescriptor> lockOperationalUnits(UUID toolId) {
            return List.copyOf(units.values());
        }

        @Override
        public Optional<OperationalUnitDescriptor> findUnit(UUID unitId) {
            return Optional.ofNullable(units.get(unitId));
        }

        @Override
        public void sendToMaintenance(UUID unitId, String reason) {
            maintenanceUnits.add(unitId);
        }

        @Override
        public void retire(UUID unitId, String reason) {
            retiredUnits.add(unitId);
        }
    }

    static class InMemoryToolCatalog implements ToolCatalogPort {
        private final Map<UUID, String> tools = new HashMap<>();

        void registerTool(UUID toolId, String name) {
            tools.put(toolId, name);
        }

        @Override
        public Optional<RentableToolDescriptor> findRentableTool(UUID toolId) {
            String name = tools.get(toolId);
            if (name == null) return Optional.empty();
            return Optional.of(new RentableToolDescriptor(
                    toolId,
                    Ids.newId(),
                    name,
                    true,
                    com.rentatools.rental.domain.model.pricing.RateCard.of(
                            Money.of("50.00"),
                            null,
                            null,
                            Money.of("100.00")
                    )
            ));
        }
    }

    static class InMemoryFileStorage implements FileStorage {
        @Override
        public StoredFile store(String folder, String originalFilename, String contentType, InputStream content, long size) {
            return new StoredFile(folder + "/" + originalFilename, contentType, size);
        }

        @Override
        public InputStream open(String storageKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String storageKey) {
        }
    }
}
