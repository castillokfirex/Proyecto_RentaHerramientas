package com.rentatools.inventory.infrastructure.adapter.out.persistence;

import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImage;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolImageJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolRateJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.repository.SpringDataToolRepository;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia JPA para el agregado {@link Tool}.
 *
 * <p>Implementa el desplazamiento temporal de posiciones ({@code position + 1000}) al actualizar
 * o reordenar imágenes para respetar el índice único {@code uq_tool_images_tool_position (tool_id, position)}
 * descrito en {@code DDL-4FN.md} §8.
 */
@Repository
@Transactional
public class ToolPersistenceAdapter implements ToolRepository {

    private static final int TEMP_POSITION_OFFSET = 1000;

    private final SpringDataToolRepository repository;
    private final InventoryPersistenceMapper mapper;
    private final EntityManager entityManager;

    public ToolPersistenceAdapter(
            SpringDataToolRepository repository,
            InventoryPersistenceMapper mapper,
            EntityManager entityManager
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    @Override
    public Tool save(Tool tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        UUID toolUuid = tool.id().value();

        ToolJpaEntity entity = repository.findById(toolUuid).orElseGet(() -> {
            ToolJpaEntity fresh = new ToolJpaEntity();
            fresh.setId(toolUuid);
            return fresh;
        });

        entity.setProviderId(tool.providerId().value());
        entity.setName(tool.name());
        entity.setBrand(tool.brand());
        entity.setModel(tool.model());
        entity.setDescription(tool.description());
        entity.setDepositAmount(tool.depositAmount().amount());
        entity.setStatus(tool.status());
        entity.setCreatedAt(tool.createdAt());
        entity.setUpdatedAt(tool.updatedAt());

        Set<UUID> newCategoryIds = new LinkedHashSet<>();
        for (CategoryId categoryId : tool.categoryIds()) {
            newCategoryIds.add(categoryId.value());
        }
        entity.getCategoryIds().clear();
        entity.getCategoryIds().addAll(newCategoryIds);

        syncRates(tool, entity);
        syncImagesWithTemporaryPositionShift(tool, entity);

        ToolJpaEntity saved = repository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tool> findById(ToolId id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Tool> findByProviderId(ProviderId providerId, PageQuery pageQuery) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");

        Page<ToolJpaEntity> page = repository.findByProviderIdOrderByCreatedAtDesc(
                providerId.value(),
                PageRequest.of(pageQuery.page(), pageQuery.size())
        );

        List<Tool> items = page.getContent().stream()
                .map(mapper::toDomain)
                .toList();

        return new PageResult<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tool> findByProviderIdAndNameIgnoreCase(ProviderId providerId, String name) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        return repository.findByProviderIdAndNameIgnoreCase(providerId.value(), name)
                .map(mapper::toDomain);
    }

    private void syncRates(Tool tool, ToolJpaEntity entity) {
        Map<RatePeriod, ToolRate> desiredRates = tool.rates();
        entity.getRates().removeIf(existing -> !desiredRates.containsKey(existing.getPeriod()));

        Map<RatePeriod, ToolRateJpaEntity> currentByPeriod = new LinkedHashMap<>();
        for (ToolRateJpaEntity rateEntity : entity.getRates()) {
            currentByPeriod.put(rateEntity.getPeriod(), rateEntity);
        }

        for (ToolRate desired : desiredRates.values()) {
            ToolRateJpaEntity existingRate = currentByPeriod.get(desired.period());
            if (existingRate != null) {
                existingRate.setPrice(desired.price().amount());
            } else {
                ToolRateJpaEntity created = new ToolRateJpaEntity();
                created.setToolId(tool.id().value());
                created.setTool(entity);
                created.setPeriod(desired.period());
                created.setPrice(desired.price().amount());
                entity.getRates().add(created);
            }
        }
    }

    private void syncImagesWithTemporaryPositionShift(Tool tool, ToolJpaEntity entity) {
        Map<UUID, ToolImage> desiredById = new LinkedHashMap<>();
        for (ToolImage img : tool.images()) {
            desiredById.put(img.id().value(), img);
        }

        boolean hadExistingImages = !entity.getImages().isEmpty();
        boolean removedAny = entity.getImages().removeIf(existing -> !desiredById.containsKey(existing.getId()));
        if (removedAny) {
            entityManager.flush();
        }

        // Si quedan imágenes persistidas cuyo orden puede haber cambiado, desplazamos sus posiciones
        // temporalmente a (position + 1000) antes de asignar las definitivas (0..N-1).
        if (hadExistingImages && !entity.getImages().isEmpty()) {
            boolean anyPositionChanged = false;
            for (ToolImageJpaEntity existingImg : entity.getImages()) {
                ToolImage desired = desiredById.get(existingImg.getId());
                if (desired != null && existingImg.getPosition() != desired.position()) {
                    anyPositionChanged = true;
                    break;
                }
            }
            if (anyPositionChanged) {
                for (ToolImageJpaEntity existingImg : entity.getImages()) {
                    existingImg.setPosition(existingImg.getPosition() + TEMP_POSITION_OFFSET);
                }
                entityManager.flush();
            }
        }

        Map<UUID, ToolImageJpaEntity> currentById = new LinkedHashMap<>();
        for (ToolImageJpaEntity existingImg : entity.getImages()) {
            currentById.put(existingImg.getId(), existingImg);
        }

        for (ToolImage desired : tool.images()) {
            ToolImageJpaEntity existingImg = currentById.get(desired.id().value());
            if (existingImg != null) {
                existingImg.setPosition(desired.position());
            } else {
                ToolImageJpaEntity created = new ToolImageJpaEntity();
                created.setId(desired.id().value());
                created.setTool(entity);
                created.setStorageKey(desired.storageKey());
                created.setContentType(desired.contentType());
                created.setSizeBytes(desired.sizeBytes());
                created.setPosition(desired.position());
                created.setUploadedAt(desired.uploadedAt());
                entity.getImages().add(created);
            }
        }
    }
}
