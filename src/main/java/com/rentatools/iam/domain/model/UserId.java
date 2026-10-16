package com.rentatools.iam.domain.model;

import com.rentatools.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador tipado del agregado {@link User}.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "userId value must not be null");
    }

    public static UserId newId() {
        return new UserId(Ids.newId());
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        Objects.requireNonNull(value, "userId string must not be null");
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
