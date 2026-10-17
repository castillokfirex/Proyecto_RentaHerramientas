package com.rentatools.inventory.infrastructure.adapter.out.persistence.entity;

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
 * Entidad JPA mapeada contra la tabla {@code tool_images} de {@code V2__inventory.sql}.
 */
@Entity
@Table(name = "tool_images")
public class ToolImageJpaEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", nullable = false, columnDefinition = "BINARY(16)")
    private ToolJpaEntity tool;

    @Column(name = "storage_key", nullable = false, length = 255, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, columnDefinition = "int unsigned")
    private int sizeBytes;

    @Column(name = "position", nullable = false, columnDefinition = "smallint unsigned")
    private Short position;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public ToolImageJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ToolJpaEntity getTool() {
        return tool;
    }

    public void setTool(ToolJpaEntity tool) {
        this.tool = tool;
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

    public Short getPosition() {
        return position;
    }

    public void setPosition(Short position) {
        this.position = position;
    }

    public void setPosition(int position) {
        this.position = (short) position;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
