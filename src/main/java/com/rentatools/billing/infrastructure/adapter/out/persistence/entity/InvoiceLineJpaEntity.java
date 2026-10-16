package com.rentatools.billing.infrastructure.adapter.out.persistence.entity;

import com.rentatools.billing.domain.model.invoice.LineConcept;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code invoice_lines} de {@code V4__billing.sql}.
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLineJpaEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceJpaEntity invoice;

    @Column(name = "reservation_unit_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID reservationUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "concept", nullable = false, length = 10)
    private LineConcept concept;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public InvoiceLineJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public InvoiceJpaEntity getInvoice() {
        return invoice;
    }

    public void setInvoice(InvoiceJpaEntity invoice) {
        this.invoice = invoice;
    }

    public UUID getReservationUnitId() {
        return reservationUnitId;
    }

    public void setReservationUnitId(UUID reservationUnitId) {
        this.reservationUnitId = reservationUnitId;
    }

    public LineConcept getConcept() {
        return concept;
    }

    public void setConcept(LineConcept concept) {
        this.concept = concept;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
