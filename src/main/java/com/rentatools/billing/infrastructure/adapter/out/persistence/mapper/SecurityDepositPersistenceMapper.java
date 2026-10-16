package com.rentatools.billing.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.billing.domain.model.deposit.DepositId;
import com.rentatools.billing.domain.model.deposit.DepositTransaction;
import com.rentatools.billing.domain.model.deposit.Retention;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.DepositRetentionJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.DepositTransactionJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.SecurityDepositJpaEntity;
import com.rentatools.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper bidireccional entre el agregado {@link SecurityDeposit} y sus entidades JPA.
 */
@Component
public class SecurityDepositPersistenceMapper {

    public SecurityDepositJpaEntity toNewEntity(SecurityDeposit deposit) {
        SecurityDepositJpaEntity entity = new SecurityDepositJpaEntity();
        entity.setId(deposit.id().value());
        entity.setReservationId(deposit.reservationId());
        entity.setRequiredAmount(deposit.requiredAmount().amount());
        entity.setCreatedAt(deposit.createdAt());
        entity.setSettledAt(deposit.settledAt());

        for (DepositTransaction tx : deposit.transactions()) {
            entity.getTransactions().add(toTransactionEntity(tx, entity));
        }
        return entity;
    }

    public SecurityDepositJpaEntity updateEntity(SecurityDepositJpaEntity existing, SecurityDeposit deposit) {
        existing.setRequiredAmount(deposit.requiredAmount().amount());
        existing.setSettledAt(deposit.settledAt());

        Set<UUID> existingTxIds = existing.getTransactions().stream()
                .map(DepositTransactionJpaEntity::getId)
                .collect(Collectors.toSet());

        for (DepositTransaction tx : deposit.transactions()) {
            if (!existingTxIds.contains(tx.id())) {
                existing.getTransactions().add(toTransactionEntity(tx, existing));
            }
        }
        return existing;
    }

    public List<DepositRetentionJpaEntity> toRetentionEntities(SecurityDeposit deposit) {
        return deposit.retentions().stream()
                .map(r -> new DepositRetentionJpaEntity(
                        r.invoiceId().value(),
                        r.amount().amount(),
                        r.appliedAt()
                ))
                .toList();
    }

    public SecurityDeposit toDomain(
            SecurityDepositJpaEntity entity,
            List<DepositRetentionJpaEntity> retentionEntities
    ) {
        List<DepositTransaction> transactions = entity.getTransactions().stream()
                .map(t -> new DepositTransaction(
                        t.getId(),
                        t.getKind(),
                        Money.of(t.getAmount()),
                        t.getMethod(),
                        t.getReference(),
                        t.getOccurredAt(),
                        t.getRecordedBy()
                ))
                .toList();

        List<Retention> retentions = retentionEntities.stream()
                .map(r -> new Retention(
                        InvoiceId.of(r.getInvoiceId()),
                        Money.of(r.getAmount()),
                        r.getAppliedAt()
                ))
                .toList();

        return SecurityDeposit.reconstitute(
                DepositId.of(entity.getId()),
                entity.getReservationId(),
                Money.of(entity.getRequiredAmount()),
                entity.getCreatedAt(),
                entity.getSettledAt(),
                transactions,
                retentions,
                entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    private static DepositTransactionJpaEntity toTransactionEntity(
            DepositTransaction tx,
            SecurityDepositJpaEntity parent
    ) {
        DepositTransactionJpaEntity e = new DepositTransactionJpaEntity();
        e.setId(tx.id());
        e.setDeposit(parent);
        e.setKind(tx.kind());
        e.setAmount(tx.amount().amount());
        e.setMethod(tx.method());
        e.setReference(tx.reference());
        e.setOccurredAt(tx.occurredAt());
        e.setRecordedBy(tx.recordedBy());
        return e;
    }
}
