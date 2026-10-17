package com.rentatools.rental.application.port.out;

import java.util.UUID;

/**
 * Puerto de salida (ACL) hacia el contexto {@code iam} para verificar estado de clientes y proveedores.
 */
public interface UserVerificationPort {

    boolean isActiveClient(UUID userId);

    boolean isApprovedProvider(UUID userId);
}
