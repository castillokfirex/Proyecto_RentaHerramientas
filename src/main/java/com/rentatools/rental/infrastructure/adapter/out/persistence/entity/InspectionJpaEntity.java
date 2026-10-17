package com.rentatools.rental.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code inspections} de {@code V3__rental.sql}.
 */
@Entity
@Table(name = "inspections")
public class InspectionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "reservation_unit_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID reservationUnitId;

    @Column(name = "type", nullable = false, length = 10)
    private String type;

    @Column(name = "inspector_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inspectorId;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "overall_condition", nullable = false, length = 20)
    private String overallCondition;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InspectionResultJpaEntity> results = new ArrayList<>();

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InspectionPhotoJpaEntity> photos = new ArrayList<>();

    public InspectionJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReservationUnitId() {
        return reservationUnitId;
    }

    public void setReservationUnitId(UUID reservationUnitId) {
        this.reservationUnitId = reservationUnitId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public UUID getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(UUID inspectorId) {
        this.inspectorId = inspectorId;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(Instant performedAt) {
        this.performedAt = performedAt;
    }

    public String getOverallCondition() {
        return overallCondition;
    }

    public void setOverallCondition(String overallCondition) {
        this.overallCondition = overallCondition;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public List<InspectionResultJpaEntity> getResults() {
        return results;
    }

    public void setResults(List<InspectionResultJpaEntity> results) {
        this.results = results;
    }

    public List<InspectionPhotoJpaEntity> getPhotos() {
        return photos;
    }

    public void setPhotos(List<InspectionPhotoJpaEntity> photos) {
        this.photos = photos;
    }
}
