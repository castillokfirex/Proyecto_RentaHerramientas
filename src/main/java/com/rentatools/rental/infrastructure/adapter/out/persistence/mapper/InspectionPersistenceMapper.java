package com.rentatools.rental.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.domain.model.inspection.ChecklistResult;
import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionId;
import com.rentatools.rental.domain.model.inspection.InspectionPhoto;
import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.inspection.ResultValue;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionPhotoJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionResultJpaEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper entre el agregado {@link Inspection} y la entidad JPA {@link InspectionJpaEntity}.
 */
public final class InspectionPersistenceMapper {

    private InspectionPersistenceMapper() {
    }

    public static Inspection toDomain(InspectionJpaEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");

        List<ChecklistResult> domainResults = new ArrayList<>();
        if (entity.getResults() != null) {
            for (InspectionResultJpaEntity r : entity.getResults()) {
                domainResults.add(ChecklistResult.of(
                        ChecklistItemId.of(r.getChecklistItemId() != null ? r.getChecklistItemId().intValue() : 0),
                        ResultValue.valueOf(r.getResult()),
                        r.getNote()
                ));
            }
        }

        List<InspectionPhoto> domainPhotos = new ArrayList<>();
        if (entity.getPhotos() != null) {
            for (InspectionPhotoJpaEntity p : entity.getPhotos()) {
                domainPhotos.add(new InspectionPhoto(
                        p.getId(),
                        p.getStorageKey(),
                        p.getContentType(),
                        p.getSizeBytes(),
                        p.getUploadedAt()
                ));
            }
        }

        return Inspection.reconstruct(
                InspectionId.of(entity.getId()),
                ReservationUnitId.of(entity.getReservationUnitId()),
                InspectionType.valueOf(entity.getType()),
                entity.getInspectorId(),
                entity.getPerformedAt(),
                OverallCondition.valueOf(entity.getOverallCondition()),
                entity.getObservations(),
                domainResults,
                domainPhotos
        );
    }

    public static InspectionJpaEntity toJpaEntity(Inspection domain) {
        Objects.requireNonNull(domain, "domain must not be null");

        InspectionJpaEntity entity = new InspectionJpaEntity();
        entity.setId(domain.id().value());
        entity.setReservationUnitId(domain.reservationUnitId().value());
        entity.setType(domain.type().name());
        entity.setInspectorId(domain.inspectorId());
        entity.setPerformedAt(domain.performedAt());
        entity.setOverallCondition(domain.overallCondition().name());
        entity.setObservations(domain.observations());

        List<InspectionResultJpaEntity> resultEntities = new ArrayList<>();
        for (ChecklistResult r : domain.results()) {
            InspectionResultJpaEntity re = new InspectionResultJpaEntity();
            re.setInspectionId(domain.id().value());
            re.setChecklistItemId((short) r.checklistItemId().value());
            re.setResult(r.result().name());
            re.setNote(r.note());
            re.setInspection(entity);
            resultEntities.add(re);
        }
        entity.setResults(resultEntities);

        List<InspectionPhotoJpaEntity> photoEntities = new ArrayList<>();
        for (InspectionPhoto p : domain.photos()) {
            InspectionPhotoJpaEntity pe = new InspectionPhotoJpaEntity();
            pe.setId(p.id());
            pe.setInspectionId(domain.id().value());
            pe.setStorageKey(p.storageKey());
            pe.setContentType(p.contentType());
            pe.setSizeBytes(p.sizeBytes());
            pe.setUploadedAt(p.uploadedAt());
            pe.setInspection(entity);
            photoEntities.add(pe);
        }
        entity.setPhotos(photoEntities);

        return entity;
    }

    public static void updateManagedEntity(InspectionJpaEntity managed, Inspection domain) {
        Objects.requireNonNull(managed, "managed must not be null");
        Objects.requireNonNull(domain, "domain must not be null");

        managed.setOverallCondition(domain.overallCondition().name());
        managed.setObservations(domain.observations());

        // Sincronizar fotos anadidas
        Set<UUID> existingPhotoIds = managed.getPhotos().stream()
                .map(InspectionPhotoJpaEntity::getId)
                .collect(Collectors.toSet());

        for (InspectionPhoto p : domain.photos()) {
            if (!existingPhotoIds.contains(p.id())) {
                InspectionPhotoJpaEntity pe = new InspectionPhotoJpaEntity();
                pe.setId(p.id());
                pe.setInspectionId(managed.getId());
                pe.setStorageKey(p.storageKey());
                pe.setContentType(p.contentType());
                pe.setSizeBytes(p.sizeBytes());
                pe.setUploadedAt(p.uploadedAt());
                pe.setInspection(managed);
                managed.getPhotos().add(pe);
            }
        }
    }
}
