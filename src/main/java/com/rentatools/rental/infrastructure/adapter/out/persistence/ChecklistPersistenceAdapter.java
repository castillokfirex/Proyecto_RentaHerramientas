package com.rentatools.rental.infrastructure.adapter.out.persistence;

import com.rentatools.rental.application.port.out.ChecklistItemRepository;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ChecklistItemJpaEntity;
import com.rentatools.rental.infrastructure.adapter.out.persistence.mapper.ChecklistPersistenceMapper;
import com.rentatools.rental.infrastructure.adapter.out.persistence.repository.SpringDataChecklistItemRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia JPA que implementa {@link ChecklistItemRepository}.
 */
@Component
@Transactional
public class ChecklistPersistenceAdapter implements ChecklistItemRepository {

    private final SpringDataChecklistItemRepository repository;

    public ChecklistPersistenceAdapter(SpringDataChecklistItemRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public ChecklistItem save(ChecklistItem item) {
        Objects.requireNonNull(item, "item must not be null");
        ChecklistItemJpaEntity saved;
        if (item.id() != null) {
            Optional<ChecklistItemJpaEntity> existing = repository.findById((short) item.id().value());
            if (existing.isPresent()) {
                ChecklistItemJpaEntity managed = existing.get();
                ChecklistPersistenceMapper.updateManagedEntity(managed, item);
                saved = repository.saveAndFlush(managed);
            } else {
                saved = repository.saveAndFlush(ChecklistPersistenceMapper.toJpaEntity(item));
            }
        } else {
            saved = repository.saveAndFlush(ChecklistPersistenceMapper.toJpaEntity(item));
        }
        return ChecklistPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChecklistItem> findById(ChecklistItemId id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById((short) id.value())
                .map(ChecklistPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChecklistItem> findByCode(String code) {
        Objects.requireNonNull(code, "code must not be null");
        return repository.findByCode(code.trim().toUpperCase())
                .map(ChecklistPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChecklistItem> findAll() {
        return repository.findAll().stream()
                .map(ChecklistPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChecklistItem> findAllActive() {
        return repository.findByActiveTrue().stream()
                .map(ChecklistPersistenceMapper::toDomain)
                .toList();
    }
}
