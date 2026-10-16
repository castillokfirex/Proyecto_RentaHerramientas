package com.rentatools.billing.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code deposit_retentions} de {@code V4__billing.sql}.
 * Por 3FN, no contiene {@code deposit_id} ya que se relaciona con la garantía a través
 * de {@code invoice_id -> invoices.reservation_id -> security_deposits.reservation_id}.
 */
@Entity
@Table(name = "deposit_retentions")
public class DepositRetentionJpaEntity {

    @Id
    @Column(name = "invoice_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID invoiceId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    public DepositRetentionJpaEntity() {
    }

    public DepositRetentionJpaEntity(UUID invoiceId, BigDecimal amount, Instant appliedAt) {
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.appliedAt = appliedAt;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }
}
