package com.rentatools.iam.infrastructure.adapter.out.persistence;

import com.rentatools.iam.application.port.out.RefreshTokenRepository;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.RefreshTokenJpaEntity;
import com.rentatools.iam.infrastructure.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.rentatools.iam.infrastructure.adapter.out.persistence.repository.SpringDataRefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de salida de persistencia para {@link RefreshTokenRepository}.
 */
@Repository
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository springDataRepository;
    private final UserPersistenceMapper mapper;

    public RefreshTokenPersistenceAdapter(
            SpringDataRefreshTokenRepository springDataRepository,
            UserPersistenceMapper mapper
    ) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = mapper.toEntity(refreshToken);
        RefreshTokenJpaEntity saved = springDataRepository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllActiveByUserId(UserId userId, Instant revokedAt) {
        springDataRepository.revokeAllActiveByUserId(userId.value(), revokedAt);
    }
}
