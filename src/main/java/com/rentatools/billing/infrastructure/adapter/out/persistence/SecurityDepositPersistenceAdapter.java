package com.rentatools.billing.infrastructure.adapter.out.persistence;

import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.DepositId;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.DepositRetentionJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.SecurityDepositJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.mapper.SecurityDepositPersistenceMapper;
import com.rentatools.billing.infrastructure.adapter.out.persistence.repository.SpringDataDepositRetentionRepository;
import com.rentatools.billing.infrastructure.adapter.out.persistence.repository.SpringDataSecurityDepositRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida de persistencia para {@link SecurityDepositRepository}.
 */
@Repository
public class SecurityDepositPersistenceAdapter implements SecurityDepositRepository {

    private final SpringDataSecurityDepositRepository springDataSecurityDepositRepository;
    private final SpringDataDepositRetentionRepository springDataDepositRetentionRepository;
    private final SecurityDepositPersistenceMapper mapper;

    public SecurityDepositPersistenceAdapter(
            SpringDataSecurityDepositRepository springDataSecurityDepositRepository,
            SpringDataDepositRetentionRepository springDataDepositRetentionRepository,
            SecurityDepositPersistenceMapper mapper
    ) {
        this.springDataSecurityDepositRepository = springDataSecurityDepositRepository;
        this.springDataDepositRetentionRepository = springDataDepositRetentionRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public SecurityDeposit save(SecurityDeposit deposit) {
        SecurityDepositJpaEntity entity = springDataSecurityDepositRepository.findById(deposit.id().value())
                .map(existing -> mapper.updateEntity(existing, deposit))
                .orElseGet(() -> mapper.toNewEntity(deposit));
        SecurityDepositJpaEntity savedEntity = springDataSecurityDepositRepository.saveAndFlush(entity);

        List<DepositRetentionJpaEntity> retentionEntities = mapper.toRetentionEntities(deposit);
        if (!retentionEntities.isEmpty()) {
            springDataDepositRetentionRepository.saveAllAndFlush(retentionEntities);
        }

        List<DepositRetentionJpaEntity> allRetentions =
                springDataDepositRetentionRepository.findByReservationId(deposit.reservationId());
        return mapper.toDomain(savedEntity, allRetentions);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SecurityDeposit> findById(DepositId id) {
        return springDataSecurityDepositRepository.findById(id.value())
                .map(entity -> {
                    List<DepositRetentionJpaEntity> retentions =
                            springDataDepositRetentionRepository.findByReservationId(entity.getReservationId());
                    return mapper.toDomain(entity, retentions);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SecurityDeposit> findByReservationId(UUID reservationId) {
        return springDataSecurityDepositRepository.findByReservationId(reservationId)
                .map(entity -> {
                    List<DepositRetentionJpaEntity> retentions =
                            springDataDepositRetentionRepository.findByReservationId(reservationId);
                    return mapper.toDomain(entity, retentions);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecurityDeposit> findAll() {
        return springDataSecurityDepositRepository.findAll().stream()
                .map(entity -> {
                    List<DepositRetentionJpaEntity> retentions =
                            springDataDepositRetentionRepository.findByReservationId(entity.getReservationId());
                    return mapper.toDomain(entity, retentions);
                })
                .toList();
    }
}
