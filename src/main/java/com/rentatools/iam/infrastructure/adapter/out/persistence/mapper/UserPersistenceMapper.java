package com.rentatools.iam.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.HashedPassword;
import com.rentatools.iam.domain.model.ProviderProfile;
import com.rentatools.iam.domain.model.RefreshToken;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.ProviderProfileJpaEntity;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.RefreshTokenJpaEntity;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import java.util.HashSet;
import org.springframework.stereotype.Component;

/**
 * Mapper entre las entidades JPA de IAM y los agregados del dominio.
 */
@Component
public class UserPersistenceMapper {

    public UserJpaEntity toNewEntity(User domain) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(domain.id().value());
        applyFields(entity, domain);
        return entity;
    }

    public UserJpaEntity updateEntity(UserJpaEntity existing, User domain) {
        applyFields(existing, domain);
        return existing;
    }

    private void applyFields(UserJpaEntity entity, User domain) {
        entity.setEmail(domain.email().value());
        entity.setPasswordHash(domain.passwordHash().value());
        entity.setFullName(domain.fullName());
        entity.setPhone(domain.phone());
        entity.setStatus(domain.status());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        entity.getRoles().clear();
        entity.getRoles().addAll(domain.roles());

        ProviderProfile profile = domain.providerProfile();
        if (profile == null) {
            entity.setProviderProfile(null);
        } else {
            ProviderProfileJpaEntity profileEntity = entity.getProviderProfile();
            if (profileEntity == null) {
                profileEntity = new ProviderProfileJpaEntity();
                entity.setProviderProfile(profileEntity);
            }
            profileEntity.setBusinessName(profile.businessName());
            profileEntity.setTaxId(profile.taxId().value());
            profileEntity.setApprovedAt(profile.approvedAt());
            profileEntity.setApprovedBy(profile.approvedBy() != null ? profile.approvedBy().value() : null);
        }
    }

    public User toDomain(UserJpaEntity entity) {
        ProviderProfileJpaEntity profileEntity = entity.getProviderProfile();
        ProviderProfile profile = null;
        if (profileEntity != null) {
            profile = new ProviderProfile(
                    profileEntity.getBusinessName(),
                    TaxId.of(profileEntity.getTaxId()),
                    profileEntity.getApprovedAt(),
                    profileEntity.getApprovedBy() != null ? UserId.of(profileEntity.getApprovedBy()) : null
            );
        }

        return User.reconstitute(
                UserId.of(entity.getId()),
                Email.of(entity.getEmail()),
                HashedPassword.of(entity.getPasswordHash()),
                entity.getFullName(),
                entity.getPhone(),
                entity.getStatus(),
                new HashSet<>(entity.getRoles()),
                profile,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    public RefreshTokenJpaEntity toEntity(RefreshToken domain) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setId(domain.id());
        entity.setUserId(domain.userId().value());
        entity.setTokenHash(domain.tokenHash());
        entity.setIssuedAt(domain.issuedAt());
        entity.setExpiresAt(domain.expiresAt());
        entity.setRevokedAt(domain.revokedAt());
        return entity;
    }

    public RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstitute(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }
}
