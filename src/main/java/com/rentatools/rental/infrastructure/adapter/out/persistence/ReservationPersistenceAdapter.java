package com.rentatools.rental.infrastructure.adapter.out.persistence;

import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ReservationJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.mapper.ReservationPersistenceMapper;
import com.rentatools.rental.infrastructure.adapter.out.persistence.repository.SpringDataReservationRepository;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia JPA que implementa {@link ReservationRepository}.
 */
@Component
@Transactional
public class ReservationPersistenceAdapter implements ReservationRepository {

    private final SpringDataReservationRepository springDataRepository;
    private final EntityManager entityManager;

    public ReservationPersistenceAdapter(
            SpringDataReservationRepository springDataRepository,
            EntityManager entityManager
    ) {
        this.springDataRepository = Objects.requireNonNull(springDataRepository, "springDataRepository must not be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    @Override
    public Reservation save(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Optional<ReservationJpaEntity> existingOpt = springDataRepository.findById(reservation.id().value());
        ReservationJpaEntity saved;
        if (existingOpt.isPresent()) {
            ReservationJpaEntity managed = existingOpt.get();
            ReservationPersistenceMapper.updateManagedEntity(managed, reservation);
            saved = springDataRepository.saveAndFlush(managed);
        } else {
            ReservationJpaEntity entity = ReservationPersistenceMapper.toJpaEntity(reservation);
            entityManager.persist(entity);
            entityManager.flush();
            saved = entity;
        }
        return ReservationPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findById(ReservationId id) {
        Objects.requireNonNull(id, "id must not be null");
        return springDataRepository.findById(id.value())
                .map(ReservationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findByCode(ReservationCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return springDataRepository.findByCode(code.value())
                .map(ReservationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(ReservationCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return springDataRepository.existsByCode(code.value());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Reservation> findByClient(ClientId clientId, ReservationStatus status, PageQuery pageQuery) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
        Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<ReservationJpaEntity> page = (status == null)
                ? springDataRepository.findByClientIdOrderByCreatedAtDesc(clientId.value(), pageable)
                : springDataRepository.findByClientIdAndStatusOrderByCreatedAtDesc(clientId.value(), status.name(), pageable);
        return toPageResult(page, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Reservation> findByProvider(ProviderId providerId, ReservationStatus status, PageQuery pageQuery) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
        Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<ReservationJpaEntity> page = (status == null)
                ? springDataRepository.findByProviderIdOrderByCreatedAtDesc(providerId.value(), pageable)
                : springDataRepository.findByProviderIdAndStatusOrderByCreatedAtDesc(providerId.value(), status.name(), pageable);
        return toPageResult(page, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Reservation> findAll(ReservationStatus status, PageQuery pageQuery) {
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
        Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<ReservationJpaEntity> page = (status == null)
                ? springDataRepository.findAllByOrderByCreatedAtDesc(pageable)
                : springDataRepository.findByStatusOrderByCreatedAtDesc(status.name(), pageable);
        return toPageResult(page, pageQuery);
    }

    private static PageResult<Reservation> toPageResult(Page<ReservationJpaEntity> page, PageQuery pageQuery) {
        List<Reservation> items = page.getContent().stream()
                .map(ReservationPersistenceMapper::toDomain)
                .toList();
        return PageResult.of(items, pageQuery.page(), pageQuery.size(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findByReservationUnitId(com.rentatools.rental.domain.model.reservation.ReservationUnitId reservationUnitId) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        return springDataRepository.findByUnitsId(reservationUnitId.value())
                .map(ReservationPersistenceMapper::toDomain);
    }
}
