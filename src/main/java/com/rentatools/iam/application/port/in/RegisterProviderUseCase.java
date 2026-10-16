package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.RegisterProviderCommand;
import com.rentatools.iam.application.dto.UserView;

/**
 * Caso de uso para el registro público de proveedores (pendientes de aprobación administrativa).
 */
public interface RegisterProviderUseCase {

    UserView registerProvider(RegisterProviderCommand command);
}
