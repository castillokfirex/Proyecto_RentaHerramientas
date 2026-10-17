package com.rentatools.inventory.infrastructure.adapter.out.persistence;

import com.rentatools.inventory.application.port.out.CategoryRepository;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.CategoryJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.repository.SpringDataCategoryRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia JPA para el agregado {@link Category}.
 */
@Repository
@Transactional
public class CategoryPersistenceAdapter implements CategoryRepository {

    private final SpringDataCategoryRepository repository;
    private final InventoryPersistenceMapper mapper;

    public CategoryPersistenceAdapter(
            SpringDataCategoryRepository repository,
            InventoryPersistenceMapper mapper
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Category save(Category category) {
        Objects.requireNonNull(category, "category must not be null");
        CategoryJpaEntity entity = repository.findById(category.id().value())
                .orElseGet(CategoryJpaEntity::new);
        mapper.updateEntity(category, entity);
        CategoryJpaEntity saved = repository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(CategoryId id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findByParentIdAndNameIgnoreCase(CategoryId parentId, String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (parentId == null) {
            return repository.findByParentIdIsNullAndNameIgnoreCase(name).map(mapper::toDomain);
        }
        return repository.findByParentIdAndNameIgnoreCase(parentId.value(), name).map(mapper::toDomain);
    }
}
