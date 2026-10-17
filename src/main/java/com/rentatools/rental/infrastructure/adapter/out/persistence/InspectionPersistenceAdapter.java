package com.rentatools.rental.infrastructure.adapter.out.persistence;

import com.rentatools.rental.application.port.out.InspectionRepository;
import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionId;
import com.rentatools.rental.domain.model.inspection.InspectionPhoto;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionPhotoJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.mapper.InspectionPersistenceMapper;
import com.rentatools.rental.infrastructure.adapter.out.persistence.repository.SpringDataInspectionPhotoRepository;
import com.rentatools.rental.infrastructure.adapter.out.persistence.repository.SpringDataInspectionRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia JPA que implementa {@link InspectionRepository}.
 */
@Component
@Transactional
public class InspectionPersistenceAdapter implements InspectionRepository {

    private final SpringDataInspectionRepository inspectionRepository;
    private final SpringDataInspectionPhotoRepository photoRepository;
    private final EntityManager entityManager;

    public InspectionPersistenceAdapter(
            SpringDataInspectionRepository inspectionRepository,
            SpringDataInspectionPhotoRepository photoRepository,
            EntityManager entityManager
    ) {
        this.inspectionRepository = Objects.requireNonNull(inspectionRepository, "inspectionRepository must not be null");
        this.photoRepository = Objects.requireNonNull(photoRepository, "photoRepository must not be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    @Override
    public Inspection save(Inspection inspection) {
        Objects.requireNonNull(inspection, "inspection must not be null");
        Optional<InspectionJpaEntity> existing = inspectionRepository.findById(inspection.id().value());
        InspectionJpaEntity saved;
        if (existing.isPresent()) {
            InspectionJpaEntity managed = existing.get();
            InspectionPersistenceMapper.updateManagedEntity(managed, inspection);
            saved = inspectionRepository.saveAndFlush(managed);
        } else {
            InspectionJpaEntity entity = InspectionPersistenceMapper.toJpaEntity(inspection);
            entityManager.persist(entity);
            entityManager.flush();
            saved = entity;
        }
        return InspectionPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inspection> findById(InspectionId id) {
        Objects.requireNonNull(id, "id must not be null");
        return inspectionRepository.findById(id.value())
                .map(InspectionPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inspection> findByReservationUnitId(ReservationUnitId reservationUnitId) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        return inspectionRepository.findByReservationUnitIdOrderByPerformedAtAsc(reservationUnitId.value()).stream()
                .map(InspectionPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inspection> findByReservationId(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return inspectionRepository.findByReservationId(reservationId.value()).stream()
                .map(InspectionPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InspectionPhoto> findPhotoById(UUID photoId) {
        Objects.requireNonNull(photoId, "photoId must not be null");
        return photoRepository.findById(photoId).map(p -> new InspectionPhoto(
                p.getId(),
                p.getStorageKey(),
                p.getContentType(),
                p.getSizeBytes(),
                p.getUploadedAt()
        ));
    }
}
