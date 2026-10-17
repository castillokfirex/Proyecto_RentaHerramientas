package com.rentatools.inventory.infrastructure.adapter.out.iam;

import com.rentatools.iam.api.UserDirectory;
import com.rentatools.inventory.application.port.out.ProviderVerificationPort;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Adaptador ACL de salida que verifica si un proveedor se encuentra aprobado
 * delegando en el contrato público {@link UserDirectory} del contexto {@code iam}.
 */
@Component
public class IamProviderVerificationAdapter implements ProviderVerificationPort {

    private final UserDirectory userDirectory;

    public IamProviderVerificationAdapter(UserDirectory userDirectory) {
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory must not be null");
    }

    @Override
    public boolean isApprovedProvider(ProviderId providerId) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        return userDirectory.isApprovedProvider(providerId.value());
    }
}
