package com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImage;
import com.rentatools.inventory.domain.model.tool.ToolImageId;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.CategoryJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolImageJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolRateJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolUnitJpaEntity;
import com.rentatools.shared.domain.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mapper entre los agregados de dominio de {@code inventory} y sus entidades JPA.
 */
@Component
public class InventoryPersistenceMapper {

    public Category toDomain(CategoryJpaEntity entity) {
        return Category.rehydrate(
                CategoryId.of(entity.getId()),
                entity.getParentId() != null ? CategoryId.of(entity.getParentId()) : null,
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    public void updateEntity(Category domain, CategoryJpaEntity entity) {
        entity.setId(domain.id().value());
        entity.setParentId(domain.parentId() != null ? domain.parentId().value() : null);
        entity.setName(domain.name());
        entity.setDescription(domain.description());
        entity.setActive(domain.active());
        entity.setCreatedAt(domain.createdAt());
    }

    public Tool toDomain(ToolJpaEntity entity) {
        Set<CategoryId> categoryIds = new LinkedHashSet<>();
        if (entity.getCategoryIds() != null) {
            for (UUID catId : entity.getCategoryIds()) {
                categoryIds.add(CategoryId.of(catId));
            }
        }

        List<ToolRate> rates = new ArrayList<>();
        if (entity.getRates() != null) {
            for (ToolRateJpaEntity rateEntity : entity.getRates()) {
                rates.add(new ToolRate(rateEntity.getPeriod(), Money.of(rateEntity.getPrice())));
            }
        }

        List<ToolImage> images = new ArrayList<>();
        if (entity.getImages() != null) {
            List<ToolImageJpaEntity> sortedImages = new ArrayList<>(entity.getImages());
            sortedImages.sort(Comparator.comparingInt(ToolImageJpaEntity::getPosition));
            for (int i = 0; i < sortedImages.size(); i++) {
                ToolImageJpaEntity imgEntity = sortedImages.get(i);
                images.add(new ToolImage(
                        ToolImageId.of(imgEntity.getId()),
                        i,
                        imgEntity.getStorageKey(),
                        imgEntity.getContentType(),
                        imgEntity.getSizeBytes(),
                        imgEntity.getUploadedAt()
                ));
            }
        }

        return Tool.rehydrate(
                ToolId.of(entity.getId()),
                ProviderId.of(entity.getProviderId()),
                entity.getName(),
                entity.getBrand(),
                entity.getModel(),
                entity.getDescription(),
                Money.of(entity.getDepositAmount()),
                entity.getStatus(),
                categoryIds,
                rates,
                images,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    public ToolUnit toDomain(ToolUnitJpaEntity entity) {
        return ToolUnit.rehydrate(
                ToolUnitId.of(entity.getId()),
                ToolId.of(entity.getToolId()),
                SerialNumber.of(entity.getSerialNumber()),
                entity.getStatus(),
                entity.getAcquiredOn(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    public void updateEntity(ToolUnit domain, ToolUnitJpaEntity entity) {
        entity.setId(domain.id().value());
        entity.setToolId(domain.toolId().value());
        entity.setSerialNumber(domain.serialNumber().value());
        entity.setStatus(domain.status());
        entity.setAcquiredOn(domain.acquiredOn());
        entity.setNotes(domain.notes());
        entity.setCreatedAt(domain.createdAt());
    }
}
