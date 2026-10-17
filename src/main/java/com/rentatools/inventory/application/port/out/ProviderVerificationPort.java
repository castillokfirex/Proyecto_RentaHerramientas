package com.rentatools.inventory.application.port.out;

import com.rentatools.inventory.domain.model.tool.ProviderId;

/**
 * Puerto de salida (ACL hacia IAM) para verificar si un proveedor se encuentra en estado {@code APPROVED}.
 */
public interface ProviderVerificationPort {

    boolean isApprovedProvider(ProviderId providerId);
}
