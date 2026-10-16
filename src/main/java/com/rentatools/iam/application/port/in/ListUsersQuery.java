package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.UserView;
import com.rentatools.iam.domain.model.UserStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.ActorRole;

/**
 * Consulta administrativa paginada para listar usuarios con filtros opcionales de rol y estado.
 */
public interface ListUsersQuery {

    PageResult<UserView> listUsers(PageQuery pageQuery, ActorRole roleFilter, UserStatus statusFilter);

    default PageResult<UserView> listUsers(PageQuery pageQuery) {
        return listUsers(pageQuery, null, null);
    }
}
