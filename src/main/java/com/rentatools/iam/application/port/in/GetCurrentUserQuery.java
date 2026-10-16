package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.UserView;
import java.util.UUID;

/**
 * Consulta para obtener los datos del usuario autenticado o por identificador.
 */
public interface GetCurrentUserQuery {

    UserView getById(UUID userId);
}
