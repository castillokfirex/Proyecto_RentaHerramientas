package com.rentatools.rental.infrastructure.adapter.out.iam;

import com.rentatools.iam.api.UserDirectory;
import com.rentatools.rental.application.port.out.UserVerificationPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador anticorrupción (ACL) que implementa {@link UserVerificationPort} consumiendo {@link UserDirectory}
 * de {@code iam.api}.
 */
@Component
public class IamUserVerificationAdapter implements UserVerificationPort {

    private final UserDirectory userDirectory;

    public IamUserVerificationAdapter(UserDirectory userDirectory) {
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory must not be null");
    }

    @Override
    public boolean isActiveClient(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userDirectory.isActiveClient(userId);
    }

    @Override
    public boolean isApprovedProvider(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userDirectory.isApprovedProvider(userId);
    }
}
