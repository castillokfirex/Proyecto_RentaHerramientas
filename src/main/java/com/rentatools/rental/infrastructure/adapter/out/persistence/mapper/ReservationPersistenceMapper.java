package com.rentatools.rental.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ReservationJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ReservationUnitJpaEntity;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mapper entre el agregado de dominio {@link Reservation} y las entidades JPA
 * {@link ReservationJpaEntity} / {@link ReservationUnitJpaEntity}.
 */
public final class ReservationPersistenceMapper {

    private ReservationPersistenceMapper() {
    }

    public static Reservation toDomain(ReservationJpaEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        List<ReservationUnit> domainUnits = new ArrayList<>(entity.getUnits().size());
        for (ReservationUnitJpaEntity u : entity.getUnits()) {
            domainUnits.add(ReservationUnit.reconstruct(
                    ReservationUnitId.of(u.getId()),
                    UnitId.of(u.getUnitId()),
                    Money.of(u.getDailyRate()),
                    Money.of(u.getAgreedAmount()),
                    Money.of(u.getDepositAmount())
            ));
        }

        return Reservation.reconstruct(
                ReservationId.of(entity.getId()),
                ReservationCode.of(entity.getCode()),
                ClientId.of(entity.getClientId()),
                ProviderId.of(entity.getProviderId()),
                DateRange.of(entity.getStartDate(), entity.getEndDate()),
                ReservationStatus.valueOf(entity.getStatus()),
                entity.getStatusReason(),
                domainUnits,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static ReservationJpaEntity toJpaEntity(Reservation domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        ReservationJpaEntity entity = new ReservationJpaEntity();
        entity.setId(domain.id().value());
        entity.setCode(domain.code().value());
        entity.setClientId(domain.clientId().value());
        entity.setProviderId(domain.providerId().value());
        entity.setStartDate(domain.dateRange().start());
        entity.setEndDate(domain.dateRange().end());
        entity.setStatus(domain.status().name());
        entity.setStatusReason(domain.statusReason());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        entity.setVersion(domain.version());

        List<ReservationUnitJpaEntity> unitEntities = new ArrayList<>(domain.units().size());
        for (ReservationUnit u : domain.units()) {
            ReservationUnitJpaEntity unitEntity = new ReservationUnitJpaEntity();
            unitEntity.setId(u.id().value());
            unitEntity.setReservation(entity);
            unitEntity.setUnitId(u.unitId().value());
            unitEntity.setDailyRate(u.dailyRate().amount());
            unitEntity.setAgreedAmount(u.agreedAmount().amount());
            unitEntity.setDepositAmount(u.depositAmount().amount());
            unitEntities.add(unitEntity);
        }
        entity.setUnits(unitEntities);
        return entity;
    }

    public static void updateManagedEntity(ReservationJpaEntity target, Reservation domain) {
        target.setStatus(domain.status().name());
        target.setStatusReason(domain.statusReason());
        target.setUpdatedAt(domain.updatedAt());
    }
}
