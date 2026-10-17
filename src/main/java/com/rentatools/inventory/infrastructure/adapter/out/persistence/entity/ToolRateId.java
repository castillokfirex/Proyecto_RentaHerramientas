package com.rentatools.inventory.infrastructure.adapter.out.persistence.entity;

import com.rentatools.inventory.domain.model.tool.RatePeriod;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clave primaria compuesta {@code (tool_id, period)} para {@link ToolRateJpaEntity}.
 */
public class ToolRateId implements Serializable {

    private UUID toolId;
    private RatePeriod period;

    public ToolRateId() {
    }

    public ToolRateId(UUID toolId, RatePeriod period) {
        this.toolId = toolId;
        this.period = period;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolRateId that)) {
            return false;
        }
        return Objects.equals(toolId, that.toolId) && period == that.period;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolId, period);
    }
}
