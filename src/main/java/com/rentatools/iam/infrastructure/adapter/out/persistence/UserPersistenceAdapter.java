package com.rentatools.iam.infrastructure.adapter.out.persistence;

import com.rentatools.iam.application.port.out.UserRepository;
import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.iam.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import com.rentatools.iam.infrastructure.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.rentatools.iam.infrastructure.adapter.out.persistence.repository.SpringDataUserRepository;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de salida de persistencia para {@link UserRepository}.
 */
@Repository
public class UserPersistenceAdapter implements UserRepository {

    private final SpringDataUserRepository springDataUserRepository;
    private final UserPersistenceMapper mapper;

    public UserPersistenceAdapter(
            SpringDataUserRepository springDataUserRepository,
            UserPersistenceMapper mapper
    ) {
        this.springDataUserRepository = springDataUserRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = springDataUserRepository.findById(user.id().value())
                .map(existing -> mapper.updateEntity(existing, user))
                .orElseGet(() -> mapper.toNewEntity(user));
        UserJpaEntity saved = springDataUserRepository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return springDataUserRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(Email email) {
        return springDataUserRepository.findByEmail(email.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(Email email) {
        return springDataUserRepository.existsByEmail(email.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTaxId(TaxId taxId) {
        return springDataUserRepository.existsByProviderProfileTaxId(taxId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> findAll(PageQuery pageQuery, ActorRole roleFilter, UserStatus statusFilter) {
        PageRequest pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<UserJpaEntity> page = springDataUserRepository.findFiltered(roleFilter, statusFilter, pageable);
        List<User> items = page.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return PageResult.of(items, pageQuery.page(), pageQuery.size(), page.getTotalElements());
    }
}
