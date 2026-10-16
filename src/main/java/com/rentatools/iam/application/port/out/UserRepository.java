package com.rentatools.iam.application.port.out;

import com.rentatools.iam.domain.model.Email;
import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.iam.domain.model.User;
import com.rentatools.iam.domain.model.UserId;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;
import java.util.Optional;

/**
 * Puerto de salida de persistencia para el agregado {@link User}.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    boolean existsByTaxId(TaxId taxId);

    PageResult<User> findAll(PageQuery pageQuery, ActorRole roleFilter, UserStatus statusFilter);

    default PageResult<User> findAll(PageQuery pageQuery) {
        return findAll(pageQuery, null, null);
    }
}
