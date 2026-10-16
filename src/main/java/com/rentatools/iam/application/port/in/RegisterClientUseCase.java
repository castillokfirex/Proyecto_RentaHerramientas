package com.rentatools.iam.application.port.in;

import com.rentatools.iam.application.dto.RegisterClientCommand;
import com.rentatools.iam.application.dto.UserView;

/**
 * Caso de uso para el registro público de clientes.
 */
public interface RegisterClientUseCase {

    UserView registerClient(RegisterClientCommand command);
}
