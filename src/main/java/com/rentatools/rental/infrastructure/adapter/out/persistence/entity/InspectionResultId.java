package com.rentatools.rental.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clave primaria compuesta {@code (inspection_id, checklist_item_id)} para {@link InspectionResultJpaEntity}.
 */
public class InspectionResultId implements Serializable {

    private UUID inspectionId;
    private Integer checklistItemId;

    public InspectionResultId() {
    }

    public InspectionResultId(UUID inspectionId, Integer checklistItemId) {
        this.inspectionId = inspectionId;
        this.checklistItemId = checklistItemId;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(UUID inspectionId) {
        this.inspectionId = inspectionId;
    }

    public Integer getChecklistItemId() {
        return checklistItemId;
    }

    public void setChecklistItemId(Integer checklistItemId) {
        this.checklistItemId = checklistItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InspectionResultId that)) return false;
        return Objects.equals(inspectionId, that.inspectionId)
                && Objects.equals(checklistItemId, that.checklistItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inspectionId, checklistItemId);
    }
}
