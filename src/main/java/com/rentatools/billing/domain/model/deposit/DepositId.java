package com.rentatools.billing.domain.model.deposit;

import com.rentatools.shared.domain.Ids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador tipado del agregado {@link SecurityDeposit}.
 */
public record DepositId(UUID value) {

    public DepositId {
        Objects.requireNonNull(value, "DepositId value must not be null");
    }

    public static DepositId of(UUID value) {
        return new DepositId(value);
    }

    public static DepositId newId() {
        return new DepositId(Ids.newId());
    }
}
