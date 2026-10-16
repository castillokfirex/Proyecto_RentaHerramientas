package com.rentatools.iam.infrastructure.adapter.out.persistence.repository;

import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import com.rentatools.shared.domain.ActorRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para {@link UserJpaEntity}.
 */
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ProviderProfileJpaEntity p WHERE p.taxId = :taxId")
    boolean existsByProviderProfileTaxId(@Param("taxId") String taxId);

    @Query("""
            SELECT DISTINCT u FROM UserJpaEntity u
            LEFT JOIN u.roles r
            WHERE (:role IS NULL OR r = :role)
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.createdAt DESC
            """)
    Page<UserJpaEntity> findFiltered(
            @Param("role") ActorRole role,
            @Param("status") UserStatus status,
            Pageable pageable
    );
}
