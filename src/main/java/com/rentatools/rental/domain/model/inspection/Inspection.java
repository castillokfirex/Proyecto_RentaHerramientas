package com.rentatools.rental.domain.model.inspection;

import com.rentatools.rental.domain.exception.DeliveryNotRegisteredException;
import com.rentatools.rental.domain.exception.InspectionAlreadyRegisteredException;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Raíz de agregado que modela una inspección de entrega ({@link InspectionType#DELIVERY}) o devolución
 * ({@link InspectionType#RETURN}) de una unidad reservada (`inspections`, `inspection_results`, `inspection_photos`).
 *
 * <p>Invariantes principales:</p>
 * <ul>
 *   <li>Máximo una inspección por par {@code (reservation_unit_id, type)}.</li>
 *   <li>Una inspección {@code RETURN} exige que exista previamente una inspección {@code DELIVERY} para esa unidad,
 *       y su {@code performed_at} no puede ser anterior al de la entrega.</li>
 *   <li>Coherencia del dictamen: si algún ítem del checklist está en {@code DAMAGED} o {@code MISSING},
 *       {@code overallCondition} no puede ser {@code GOOD}.</li>
 *   <li>{@code LOST} solo es válido en una inspección de tipo {@code RETURN}.</li>
 *   <li>Máximo 10 fotos por inspección.</li>
 * </ul>
 */
public final class Inspection extends AggregateRoot {

    public static final int MAX_PHOTOS = 10;

    private final InspectionId id;
    private final ReservationUnitId reservationUnitId;
    private final InspectionType type;
    private final UUID inspectorId;
    private final Instant performedAt;
    private final OverallCondition overallCondition;
    private final String observations;
    private final List<ChecklistResult> results;
    private final List<InspectionPhoto> photos;

    private Inspection(
            InspectionId id,
            ReservationUnitId reservationUnitId,
            InspectionType type,
            UUID inspectorId,
            Instant performedAt,
            OverallCondition overallCondition,
            String observations,
            List<ChecklistResult> results,
            List<InspectionPhoto> photos
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.reservationUnitId = Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.inspectorId = Objects.requireNonNull(inspectorId, "inspectorId must not be null");
        this.performedAt = Objects.requireNonNull(performedAt, "performedAt must not be null");
        this.overallCondition = Objects.requireNonNull(overallCondition, "overallCondition must not be null");
        this.observations = normalizeObservations(observations);
        this.results = validateAndCopyResults(results);

        validateConditionRules(this.type, this.overallCondition, this.results);

        this.photos = new ArrayList<>();
        if (photos != null) {
            for (InspectionPhoto photo : photos) {
                attachPhotoInternal(photo);
            }
        }
    }

    /**
     * Crea una inspección validando contra las inspecciones ya existentes de la misma unidad:
     * <ul>
     *   <li>Rechaza con {@link InspectionAlreadyRegisteredException} si ya existe una del mismo tipo.</li>
     *   <li>Si es {@link InspectionType#RETURN}, exige que exista una {@link InspectionType#DELIVERY} previa
     *       (de lo contrario lanza {@link DeliveryNotRegisteredException}) y que su fecha no sea anterior.</li>
     * </ul>
     */
    public static Inspection create(
            ReservationUnitId reservationUnitId,
            InspectionType type,
            UUID inspectorId,
            OverallCondition overallCondition,
            String observations,
            List<ChecklistResult> results,
            Collection<Inspection> existingInspectionsForUnit,
            Clock clock
    ) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        Inspection priorDelivery = null;
        if (existingInspectionsForUnit != null) {
            for (Inspection existing : existingInspectionsForUnit) {
                Objects.requireNonNull(existing, "existing inspection must not be null");
                if (!existing.reservationUnitId().equals(reservationUnitId)) {
                    continue;
                }
                if (existing.type() == type) {
                    throw new InspectionAlreadyRegisteredException(reservationUnitId, type);
                }
                if (existing.type() == InspectionType.DELIVERY) {
                    priorDelivery = existing;
                }
            }
        }

        Instant now = clock.instant();
        if (type == InspectionType.RETURN) {
            if (priorDelivery == null) {
                throw new DeliveryNotRegisteredException(reservationUnitId);
            }
            if (now.isBefore(priorDelivery.performedAt())) {
                throw new BusinessRuleViolation(
                        "RETURN_BEFORE_DELIVERY",
                        "RETURN inspection timestamp cannot be before DELIVERY inspection timestamp"
                );
            }
        }

        return new Inspection(
                InspectionId.generate(clock),
                reservationUnitId,
                type,
                inspectorId,
                now,
                overallCondition,
                observations,
                results,
                List.of()
        );
    }

    /**
     * Crea una inspección de entrega ({@link InspectionType#DELIVERY}).
     */
    public static Inspection createDelivery(
            ReservationUnitId reservationUnitId,
            UUID inspectorId,
            OverallCondition overallCondition,
            String observations,
            List<ChecklistResult> results,
            Clock clock
    ) {
        return create(
                reservationUnitId,
                InspectionType.DELIVERY,
                inspectorId,
                overallCondition,
                observations,
                results,
                List.of(),
                clock
        );
    }

    /**
     * Crea una inspección de devolución ({@link InspectionType#RETURN}) exigiendo la inspección de entrega previa.
     */
    public static Inspection createReturn(
            ReservationUnitId reservationUnitId,
            UUID inspectorId,
            OverallCondition overallCondition,
            String observations,
            List<ChecklistResult> results,
            Inspection priorDeliveryInspection,
            Clock clock
    ) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        if (priorDeliveryInspection == null) {
            throw new DeliveryNotRegisteredException(reservationUnitId);
        }
        if (priorDeliveryInspection.type() != InspectionType.DELIVERY
                || !priorDeliveryInspection.reservationUnitId().equals(reservationUnitId)) {
            throw new DeliveryNotRegisteredException(reservationUnitId);
        }
        return create(
                reservationUnitId,
                InspectionType.RETURN,
                inspectorId,
                overallCondition,
                observations,
                results,
                List.of(priorDeliveryInspection),
                clock
        );
    }

    /**
     * Reconstruye una inspección desde la capa de persistencia.
     */
    public static Inspection reconstruct(
            InspectionId id,
            ReservationUnitId reservationUnitId,
            InspectionType type,
            UUID inspectorId,
            Instant performedAt,
            OverallCondition overallCondition,
            String observations,
            List<ChecklistResult> results,
            List<InspectionPhoto> photos
    ) {
        return new Inspection(
                id,
                reservationUnitId,
                type,
                inspectorId,
                performedAt,
                overallCondition,
                observations,
                results,
                photos
        );
    }

    /**
     * Adjunta una foto a la inspección respetando el límite máximo de 10 fotos.
     */
    public InspectionPhoto attachPhoto(
            String storageKey,
            String contentType,
            long sizeBytes,
            Clock clock
    ) {
        InspectionPhoto photo = InspectionPhoto.create(storageKey, contentType, sizeBytes, clock);
        attachPhotoInternal(photo);
        return photo;
    }

    /**
     * Adjunta una foto ya construida a la inspección respetando el límite máximo de 10 fotos.
     */
    public void attachPhoto(InspectionPhoto photo) {
        attachPhotoInternal(photo);
    }

    public InspectionId id() {
        return id;
    }

    public ReservationUnitId reservationUnitId() {
        return reservationUnitId;
    }

    public InspectionType type() {
        return type;
    }

    public UUID inspectorId() {
        return inspectorId;
    }

    public Instant performedAt() {
        return performedAt;
    }

    public OverallCondition overallCondition() {
        return overallCondition;
    }

    public String observations() {
        return observations;
    }

    public List<ChecklistResult> results() {
        return results;
    }

    public List<InspectionPhoto> photos() {
        return List.copyOf(photos);
    }

    private void attachPhotoInternal(InspectionPhoto photo) {
        Objects.requireNonNull(photo, "photo must not be null");
        if (this.photos.size() >= MAX_PHOTOS) {
            throw new BusinessRuleViolation(
                    "PHOTO_LIMIT_EXCEEDED",
                    "An inspection cannot have more than " + MAX_PHOTOS + " photos"
            );
        }
        for (InspectionPhoto existing : this.photos) {
            if (existing.storageKey().equals(photo.storageKey())) {
                throw new BusinessRuleViolation(
                        "DUPLICATE_PHOTO_STORAGE_KEY",
                        "Photo storageKey '" + photo.storageKey() + "' is already attached to this inspection"
                );
            }
        }
        this.photos.add(photo);
    }

    private static List<ChecklistResult> validateAndCopyResults(List<ChecklistResult> results) {
        Objects.requireNonNull(results, "results must not be null");
        Set<ChecklistItemId> seenItems = new HashSet<>();
        for (ChecklistResult r : results) {
            Objects.requireNonNull(r, "checklist result must not be null");
            if (!seenItems.add(r.checklistItemId())) {
                throw new BusinessRuleViolation(
                        "DUPLICATE_CHECKLIST_ITEM",
                        "Duplicate checklist item id in inspection: " + r.checklistItemId().value()
                );
            }
        }
        return List.copyOf(results);
    }

    private static void validateConditionRules(
            InspectionType type,
            OverallCondition overallCondition,
            List<ChecklistResult> results
    ) {
        if (overallCondition == OverallCondition.LOST && type != InspectionType.RETURN) {
            throw new BusinessRuleViolation(
                    "LOST_NOT_ALLOWED_ON_DELIVERY",
                    "Overall condition LOST is only valid for RETURN inspections"
            );
        }
        boolean hasDamagedOrMissing = false;
        for (ChecklistResult r : results) {
            if (r.isDamagedOrMissing()) {
                hasDamagedOrMissing = true;
                break;
            }
        }
        if (hasDamagedOrMissing && overallCondition == OverallCondition.GOOD) {
            throw new BusinessRuleViolation(
                    "INCONSISTENT_OVERALL_CONDITION",
                    "Overall condition cannot be GOOD when checklist contains DAMAGED or MISSING items"
            );
        }
    }

    private static String normalizeObservations(String observations) {
        if (observations == null) {
            return null;
        }
        String trimmed = observations.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
