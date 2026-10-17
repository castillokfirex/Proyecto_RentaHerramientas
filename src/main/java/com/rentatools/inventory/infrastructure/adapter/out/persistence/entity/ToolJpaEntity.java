package com.rentatools.inventory.infrastructure.adapter.out.persistence.entity;

import com.rentatools.inventory.domain.model.tool.ToolStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entidad JPA mapeada contra la tabla {@code tools} y la tabla de colección {@code tool_categories}
 * de {@code V2__inventory.sql}.
 */
@Entity
@Table(name = "tools")
public class ToolJpaEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "provider_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID providerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "brand", length = 80)
    private String brand;

    @Column(name = "model", length = 80)
    private String model;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ToolStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tool_categories",
            joinColumns = @JoinColumn(name = "tool_id", nullable = false, columnDefinition = "BINARY(16)")
    )
    @Column(name = "category_id", nullable = false, columnDefinition = "BINARY(16)")
    private Set<UUID> categoryIds = new LinkedHashSet<>();

    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ToolRateJpaEntity> rates = new ArrayList<>();

    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<ToolImageJpaEntity> images = new ArrayList<>();

    public ToolJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public ToolStatus getStatus() {
        return status;
    }

    public void setStatus(ToolStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Set<UUID> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(Set<UUID> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public List<ToolRateJpaEntity> getRates() {
        return rates;
    }

    public void setRates(List<ToolRateJpaEntity> rates) {
        this.rates = rates;
    }

    public List<ToolImageJpaEntity> getImages() {
        return images;
    }

    public void setImages(List<ToolImageJpaEntity> images) {
        this.images = images;
    }
}
