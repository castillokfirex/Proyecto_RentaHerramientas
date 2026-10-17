package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.AttachInspectionPhotoCommand;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.application.dto.RegisterInspectionCommand;
import com.rentatools.rental.application.mapper.InspectionViewMapper;
import com.rentatools.rental.application.mapper.IntegrationEventMapper;
import com.rentatools.rental.application.port.in.AttachInspectionPhotoUseCase;
import com.rentatools.rental.application.port.in.GetInspectionQuery;
import com.rentatools.rental.application.port.in.RegisterInspectionUseCase;
import com.rentatools.rental.application.port.out.ChecklistItemRepository;
import com.rentatools.rental.application.port.out.InspectionRepository;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.domain.exception.InvalidReservationTransitionException;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.domain.model.inspection.ChecklistResult;
import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionId;
import com.rentatools.rental.domain.model.inspection.InspectionPhoto;
import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.rental.domain.service.LateReturnPolicy;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.application.port.out.StoredFile;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.integration.rental.ReservationReturned;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación que implementa el registro y consulta de inspecciones de entrega y devolución,
 * subida de fotos de evidencia y publicación de eventos de integración al completar la devolución.
 */
public class InspectionService implements
        RegisterInspectionUseCase,
        AttachInspectionPhotoUseCase,
        GetInspectionQuery {

    private final ReservationRepository reservationRepository;
    private final InspectionRepository inspectionRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final UnitInventoryPort unitInventoryPort;
    private final ToolCatalogPort toolCatalogPort;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final FileStorage fileStorage;
    private final Clock clock;
    private final LateReturnPolicy lateReturnPolicy = new LateReturnPolicy();

    public InspectionService(
            ReservationRepository reservationRepository,
            InspectionRepository inspectionRepository,
            ChecklistItemRepository checklistItemRepository,
            UnitInventoryPort unitInventoryPort,
            ToolCatalogPort toolCatalogPort,
            IntegrationEventPublisher integrationEventPublisher,
            FileStorage fileStorage,
            Clock clock
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository must not be null");
        this.inspectionRepository = Objects.requireNonNull(inspectionRepository, "inspectionRepository must not be null");
        this.checklistItemRepository = Objects.requireNonNull(checklistItemRepository, "checklistItemRepository must not be null");
        this.unitInventoryPort = Objects.requireNonNull(unitInventoryPort, "unitInventoryPort must not be null");
        this.toolCatalogPort = Objects.requireNonNull(toolCatalogPort, "toolCatalogPort must not be null");
        this.integrationEventPublisher = Objects.requireNonNull(integrationEventPublisher, "integrationEventPublisher must not be null");
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public InspectionView registerInspection(Actor actor, RegisterInspectionCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Reservation reservation = loadReservationOrThrow(command.reservationId());
        assertProviderOrAdmin(actor, reservation);

        ReservationUnitId unitId = ReservationUnitId.of(command.reservationUnitId());
        ReservationUnit resUnit = reservation.findReservationUnit(unitId)
                .orElseThrow(() -> new BusinessRuleViolation(
                        "RESERVATION_UNIT_NOT_FOUND",
                        "Reservation unit " + command.reservationUnitId() + " does not belong to reservation " + reservation.id().value()
                ));

        // Cargar inspecciones previas de la reserva para reconstruir el estado de unidades
        List<Inspection> existingForReservation = inspectionRepository.findByReservationId(reservation.id());
        Set<ReservationUnitId> deliveredUnitIds = existingForReservation.stream()
                .filter(i -> i.type() == InspectionType.DELIVERY)
                .map(Inspection::reservationUnitId)
                .collect(Collectors.toSet());
        Set<ReservationUnitId> returnedUnitIds = existingForReservation.stream()
                .filter(i -> i.type() == InspectionType.RETURN)
                .map(Inspection::reservationUnitId)
                .collect(Collectors.toSet());
        reservation.recordDeliveredUnits(deliveredUnitIds);
        reservation.recordReturnedUnits(returnedUnitIds);

        List<ChecklistResult> results = new ArrayList<>();
        if (command.results() != null) {
            for (RegisterInspectionCommand.ChecklistResultItem item : command.results()) {
                results.add(ChecklistResult.of(
                        ChecklistItemId.of(item.checklistItemId()),
                        item.result(),
                        item.note()
                ));
            }
        }

        List<Inspection> existingForUnit = inspectionRepository.findByReservationUnitId(unitId);

        Inspection inspection;
        if (command.type() == InspectionType.DELIVERY) {
            if (reservation.status() != ReservationStatus.READY_FOR_PICKUP && reservation.status() != ReservationStatus.ACTIVE) {
                throw new InvalidReservationTransitionException(reservation.status(), ReservationStatus.ACTIVE);
            }
            LocalDate today = LocalDate.now(clock);
            if (today.isBefore(reservation.dateRange().start())) {
                throw new BusinessRuleViolation(
                        "DELIVERY_BEFORE_START_DATE",
                        "Delivery cannot be registered before reservation start date (" + reservation.dateRange().start() + ")"
                );
            }

            inspection = Inspection.create(
                    unitId,
                    InspectionType.DELIVERY,
                    actor.userId(),
                    command.overallCondition(),
                    command.observations(),
                    results,
                    existingForUnit,
                    clock
            );

            if (reservation.status() == ReservationStatus.READY_FOR_PICKUP) {
                reservation.registerUnitDelivery(unitId, clock);
                reservationRepository.save(reservation);
            }
            Inspection saved = inspectionRepository.save(inspection);
            return InspectionViewMapper.toView(saved, loadChecklistCatalog());
        } else {
            if (reservation.status() != ReservationStatus.ACTIVE) {
                throw new InvalidReservationTransitionException(reservation.status(), ReservationStatus.RETURNED);
            }

            inspection = Inspection.create(
                    unitId,
                    InspectionType.RETURN,
                    actor.userId(),
                    command.overallCondition(),
                    command.observations(),
                    results,
                    existingForUnit,
                    clock
            );

            // Efectos sobre el estado físico de la unidad si hay avería grave o pérdida
            if (command.overallCondition() == OverallCondition.MAJOR_DAMAGE) {
                unitInventoryPort.sendToMaintenance(
                        resUnit.unitId().value(),
                        "Inspección de devolución: avería mayor reportada. Obs: " + (command.observations() != null ? command.observations() : "N/A")
                );
            } else if (command.overallCondition() == OverallCondition.LOST) {
                unitInventoryPort.retire(
                        resUnit.unitId().value(),
                        "Inspección de devolución: unidad declarada perdida"
                );
            }

            reservation.registerUnitReturn(unitId, clock);
            reservationRepository.save(reservation);
            Inspection saved = inspectionRepository.save(inspection);

            // Si todas las unidades han sido devueltas, la reserva transiciona a RETURNED y publica ReservationReturned
            if (reservation.status() == ReservationStatus.RETURNED) {
                List<Inspection> allInspections = inspectionRepository.findByReservationId(reservation.id());
                List<ReservationReturned.ReturnedUnit> returnedUnits = new ArrayList<>(reservation.units().size());

                for (ReservationUnit unit : reservation.units()) {
                    Inspection returnInsp = allInspections.stream()
                            .filter(i -> i.type() == InspectionType.RETURN && i.reservationUnitId().equals(unit.id()))
                            .findFirst()
                            .orElse(saved);

                    Instant performedAt = returnInsp.performedAt();
                    int lateDays = lateReturnPolicy.calculateLateDays(reservation.dateRange(), performedAt, clock);
                    String toolName = resolveToolName(unit.unitId().value());
                    boolean damageReported = returnInsp.overallCondition() != OverallCondition.GOOD;
                    boolean lost = returnInsp.overallCondition() == OverallCondition.LOST;

                    returnedUnits.add(new ReservationReturned.ReturnedUnit(
                            unit.id().value(),
                            toolName,
                            LocalDate.ofInstant(performedAt, clock.getZone()),
                            lateDays,
                            unit.dailyRate().amount(),
                            damageReported,
                            lost
                    ));
                }

                ReservationReturned integrationEvent = IntegrationEventMapper.toReservationReturned(
                        reservation,
                        returnedUnits,
                        actor.userId(),
                        clock
                );
                integrationEventPublisher.publish(integrationEvent);
            }

            return InspectionViewMapper.toView(saved, loadChecklistCatalog());
        }
    }

    @Override
    public InspectionPhotoView attachPhoto(Actor actor, AttachInspectionPhotoCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Inspection inspection = loadInspectionOrThrow(command.inspectionId());
        Reservation reservation = reservationRepository.findByReservationUnitId(inspection.reservationUnitId())
                .orElseThrow(() -> new NotFoundException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found for inspection " + inspection.id().value()
                ));
        assertProviderOrAdmin(actor, reservation);

        String normalizedContentType = command.contentType().trim().toLowerCase();
        if (!InspectionPhoto.ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BusinessRuleViolation(
                    "INVALID_PHOTO_CONTENT_TYPE",
                    "Unsupported photo content type: " + command.contentType() + ". Allowed: " + InspectionPhoto.ALLOWED_CONTENT_TYPES
            );
        }
        if (command.sizeBytes() <= 0 || command.sizeBytes() > InspectionPhoto.MAX_SIZE_BYTES) {
            throw new BusinessRuleViolation(
                    "INVALID_PHOTO_SIZE",
                    "Photo size must be between 1 and " + InspectionPhoto.MAX_SIZE_BYTES + " bytes"
            );
        }

        StoredFile stored = fileStorage.store(
                "inspections/" + inspection.id().value(),
                command.originalFilename(),
                normalizedContentType,
                command.content(),
                command.sizeBytes()
        );

        InspectionPhoto photo = inspection.attachPhoto(
                stored.storageKey(),
                stored.contentType(),
                stored.sizeBytes(),
                clock
        );
        inspectionRepository.save(inspection);

        return new InspectionPhotoView(
                photo.id(),
                inspection.id().value(),
                photo.storageKey(),
                photo.contentType(),
                photo.sizeBytes(),
                photo.uploadedAt()
        );
    }

    @Override
    public InspectionView getInspection(Actor actor, UUID inspectionId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(inspectionId, "inspectionId must not be null");

        Inspection inspection = loadInspectionOrThrow(inspectionId);
        Reservation reservation = reservationRepository.findByReservationUnitId(inspection.reservationUnitId())
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));
        reservation.assertParticipant(actor);

        return InspectionViewMapper.toView(inspection, loadChecklistCatalog());
    }

    @Override
    public List<InspectionView> listInspectionsByReservation(Actor actor, UUID reservationId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        Reservation reservation = loadReservationOrThrow(reservationId);
        reservation.assertParticipant(actor);

        List<Inspection> inspections = inspectionRepository.findByReservationId(reservation.id());
        Map<Integer, ChecklistItem> catalog = loadChecklistCatalog();

        return inspections.stream()
                .map(i -> InspectionViewMapper.toView(i, catalog))
                .toList();
    }

    @Override
    public InspectionPhotoResource getInspectionPhotoResource(Actor actor, UUID inspectionId, UUID photoId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        Objects.requireNonNull(photoId, "photoId must not be null");

        Inspection inspection = loadInspectionOrThrow(inspectionId);
        Reservation reservation = reservationRepository.findByReservationUnitId(inspection.reservationUnitId())
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));
        reservation.assertParticipant(actor);

        InspectionPhoto photo = inspection.photos().stream()
                .filter(p -> p.id().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("PHOTO_NOT_FOUND", "Photo not found: " + photoId));

        InputStream stream = fileStorage.open(photo.storageKey());
        InspectionPhotoView view = new InspectionPhotoView(
                photo.id(),
                inspection.id().value(),
                photo.storageKey(),
                photo.contentType(),
                photo.sizeBytes(),
                photo.uploadedAt()
        );

        return new InspectionPhotoResource(view, stream);
    }

    private Reservation loadReservationOrThrow(UUID reservationId) {
        return reservationRepository.findById(ReservationId.of(reservationId))
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId));
    }

    private Inspection loadInspectionOrThrow(UUID inspectionId) {
        return inspectionRepository.findById(InspectionId.of(inspectionId))
                .orElseThrow(() -> new NotFoundException("INSPECTION_NOT_FOUND", "Inspection not found: " + inspectionId));
    }

    private Map<Integer, ChecklistItem> loadChecklistCatalog() {
        Map<Integer, ChecklistItem> catalog = new HashMap<>();
        for (ChecklistItem item : checklistItemRepository.findAll()) {
            if (item.id() != null) {
                catalog.put(item.id().value(), item);
            }
        }
        return catalog;
    }

    private String resolveToolName(UUID unitId) {
        return unitInventoryPort.findUnit(unitId)
                .flatMap(u -> toolCatalogPort.findRentableTool(u.toolId()))
                .map(ToolCatalogPort.RentableToolDescriptor::name)
                .orElse("Herramienta");
    }

    private void assertProviderOrAdmin(Actor actor, Reservation reservation) {
        if (actor.is(ActorRole.ADMIN)) {
            return;
        }
        if (!reservation.providerId().value().equals(actor.userId())) {
            throw new BusinessRuleViolation(
                    "FORBIDDEN",
                    "Only the tool provider owner or an administrator can register or modify inspections"
            );
        }
    }
}
