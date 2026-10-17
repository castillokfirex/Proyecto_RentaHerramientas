package com.rentatools.inventory.application;

import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.inventory.application.port.out.CatalogReadRepository;
import com.rentatools.inventory.application.port.out.CategoryRepository;
import com.rentatools.inventory.application.port.out.ProviderVerificationPort;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.application.port.out.ToolUnitRepository;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImage;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.application.port.out.StoredFile;
import com.rentatools.shared.domain.Ids;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fakes en memoria para probar todos los casos de uso de {@code inventory} sin levantar Spring Boot.
 */
final class InventoryTestFakes {

    private InventoryTestFakes() {
    }

    static final class InMemoryCategoryRepository implements CategoryRepository {
        private final Map<CategoryId, Category> store = new LinkedHashMap<>();

        @Override
        public Category save(Category category) {
            store.put(category.id(), category);
            return category;
        }

        @Override
        public Optional<Category> findById(CategoryId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Category> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Optional<Category> findByParentIdAndNameIgnoreCase(CategoryId parentId, String name) {
            return store.values().stream()
                    .filter(c -> Objects.equals(c.parentId(), parentId))
                    .filter(c -> c.name().equalsIgnoreCase(name))
                    .findFirst();
        }
    }

    static final class InMemoryToolRepository implements ToolRepository {
        private final Map<ToolId, Tool> store = new LinkedHashMap<>();

        @Override
        public Tool save(Tool tool) {
            store.put(tool.id(), tool);
            return tool;
        }

        @Override
        public Optional<Tool> findById(ToolId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public PageResult<Tool> findByProviderId(ProviderId providerId, PageQuery pageQuery) {
            List<Tool> matching = store.values().stream()
                    .filter(t -> t.providerId().equals(providerId))
                    .toList();
            return PageResult.of(matching, pageQuery.page(), pageQuery.size(), matching.size());
        }

        @Override
        public Optional<Tool> findByProviderIdAndNameIgnoreCase(ProviderId providerId, String name) {
            return store.values().stream()
                    .filter(t -> t.providerId().equals(providerId))
                    .filter(t -> t.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        List<Tool> findAll() {
            return new ArrayList<>(store.values());
        }
    }

    static final class InMemoryToolUnitRepository implements ToolUnitRepository {
        private final Map<ToolUnitId, ToolUnit> store = new LinkedHashMap<>();

        @Override
        public ToolUnit save(ToolUnit unit) {
            store.put(unit.id(), unit);
            return unit;
        }

        @Override
        public Optional<ToolUnit> findById(ToolUnitId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<ToolUnit> findByToolId(ToolId toolId) {
            return store.values().stream()
                    .filter(u -> u.toolId().equals(toolId))
                    .toList();
        }

        @Override
        public boolean existsByToolIdAndSerialNumber(ToolId toolId, SerialNumber serialNumber) {
            return store.values().stream()
                    .anyMatch(u -> u.toolId().equals(toolId) && u.serialNumber().equals(serialNumber));
        }

        @Override
        public long countByToolIdAndStatus(ToolId toolId, UnitStatus status) {
            return store.values().stream()
                    .filter(u -> u.toolId().equals(toolId) && u.status() == status)
                    .count();
        }
    }

    static final class InMemoryProviderVerificationPort implements ProviderVerificationPort {
        private final Set<ProviderId> approvedProviders = new HashSet<>();

        void approve(ProviderId providerId) {
            approvedProviders.add(providerId);
        }

        void revoke(ProviderId providerId) {
            approvedProviders.remove(providerId);
        }

        @Override
        public boolean isApprovedProvider(ProviderId providerId) {
            return approvedProviders.contains(providerId);
        }
    }

    static final class InMemoryFileStorage implements FileStorage {
        private final Set<String> storedKeys = new LinkedHashSet<>();

        @Override
        public StoredFile store(
                String folder,
                String originalFilename,
                String contentType,
                InputStream content,
                long size
        ) {
            String key = folder + "/" + Ids.newId() + "-" + originalFilename;
            storedKeys.add(key);
            return new StoredFile(key, contentType, size);
        }

        @Override
        public InputStream open(String storageKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String storageKey) {
            storedKeys.remove(storageKey);
        }

        boolean exists(String storageKey) {
            return storedKeys.contains(storageKey);
        }
    }

    static final class InMemoryCatalogReadRepository implements CatalogReadRepository {
        private final InMemoryToolRepository toolRepository;
        private final InMemoryToolUnitRepository unitRepository;

        InMemoryCatalogReadRepository(
                InMemoryToolRepository toolRepository,
                InMemoryToolUnitRepository unitRepository
        ) {
            this.toolRepository = toolRepository;
            this.unitRepository = unitRepository;
        }

        @Override
        public PageResult<CatalogToolSummaryView> searchActiveTools(
                CatalogSearchCriteria criteria,
                PageQuery pageQuery
        ) {
            List<CatalogToolSummaryView> items = toolRepository.findAll().stream()
                    .filter(t -> t.status() == ToolStatus.ACTIVE)
                    .filter(t -> {
                        if (criteria.query() == null) {
                            return true;
                        }
                        String q = criteria.query().toLowerCase();
                        return t.name().toLowerCase().contains(q)
                                || (t.brand() != null && t.brand().toLowerCase().contains(q));
                    })
                    .filter(t -> criteria.categoryId() == null
                            || t.categoryIds().contains(CategoryId.of(criteria.categoryId())))
                    .filter(t -> {
                        ToolRate dayRate = t.rates().get(RatePeriod.DAY);
                        if (dayRate == null) {
                            return false;
                        }
                        if (criteria.minDayPrice() != null && dayRate.price().compareTo(criteria.minDayPrice()) < 0) {
                            return false;
                        }
                        return criteria.maxDayPrice() == null || dayRate.price().compareTo(criteria.maxDayPrice()) <= 0;
                    })
                    .map(t -> {
                        long available = unitRepository.countByToolIdAndStatus(t.id(), UnitStatus.AVAILABLE);
                        String coverUrl = t.coverImage()
                                .map(ToolImage::storageKey)
                                .orElse(null);
                        Set<UUID> catIds = new LinkedHashSet<>();
                        t.categoryIds().forEach(cid -> catIds.add(cid.value()));
                        return new CatalogToolSummaryView(
                                t.id().value(),
                                t.providerId().value(),
                                t.name(),
                                t.brand(),
                                t.model(),
                                t.depositAmount(),
                                t.rates().get(RatePeriod.DAY).price(),
                                coverUrl,
                                catIds,
                                available
                        );
                    })
                    .toList();

            return PageResult.of(items, pageQuery.page(), pageQuery.size(), items.size());
        }
    }
}
