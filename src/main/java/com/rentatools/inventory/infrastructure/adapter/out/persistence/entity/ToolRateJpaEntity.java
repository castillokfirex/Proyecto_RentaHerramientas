package com.rentatools.inventory.infrastructure.adapter.out.persistence.entity;

import com.rentatools.inventory.domain.model.tool.RatePeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code tool_rates} de {@code V2__inventory.sql}.
 */
@Entity
@Table(name = "tool_rates")
@IdClass(ToolRateId.class)
public class ToolRateJpaEntity {

    @Id
    @Column(name = "tool_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID toolId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 10)
    private RatePeriod period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", nullable = false, insertable = false, updatable = false)
    private ToolJpaEntity tool;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    public ToolRateJpaEntity() {
    }

    public UUID getToolId() {
        return toolId;
    }

    public void setToolId(UUID toolId) {
        this.toolId = toolId;
    }

    public RatePeriod getPeriod() {
        return period;
    }

    public void setPeriod(RatePeriod period) {
        this.period = period;
    }

    public ToolJpaEntity getTool() {
        return tool;
    }

    public void setTool(ToolJpaEntity tool) {
        this.tool = tool;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
