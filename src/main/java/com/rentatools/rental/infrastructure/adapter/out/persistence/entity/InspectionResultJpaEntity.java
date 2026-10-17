package com.rentatools.rental.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code inspection_results} de {@code V3__rental.sql}.
 */
@Entity
@Table(name = "inspection_results")
@IdClass(InspectionResultId.class)
public class InspectionResultJpaEntity {

    @Id
    @Column(name = "inspection_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inspectionId;

    @Id
    @Column(name = "checklist_item_id", nullable = false, columnDefinition = "smallint unsigned")
    private Short checklistItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false, insertable = false, updatable = false)
    private InspectionJpaEntity inspection;

    @Column(name = "result", nullable = false, length = 15)
    private String result;

    @Column(name = "note", length = 300)
    private String note;

    public InspectionResultJpaEntity() {
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(UUID inspectionId) {
        this.inspectionId = inspectionId;
    }

    public Short getChecklistItemId() {
        return checklistItemId;
    }

    public void setChecklistItemId(Short checklistItemId) {
        this.checklistItemId = checklistItemId;
    }

    public InspectionJpaEntity getInspection() {
        return inspection;
    }

    public void setInspection(InspectionJpaEntity inspection) {
        this.inspection = inspection;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
