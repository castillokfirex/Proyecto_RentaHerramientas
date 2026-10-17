package com.rentatools.rental.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code inspection_photos} de {@code V3__rental.sql}.
 */
@Entity
@Table(name = "inspection_photos")
public class InspectionPhotoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "inspection_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false, insertable = false, updatable = false)
    private InspectionJpaEntity inspection;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, columnDefinition = "int unsigned")
    private int sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public InspectionPhotoJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(UUID inspectionId) {
        this.inspectionId = inspectionId;
    }

    public InspectionJpaEntity getInspection() {
        return inspection;
    }

    public void setInspection(InspectionJpaEntity inspection) {
        this.inspection = inspection;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = (int) sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
