package com.rentatools.billing.domain.model.invoice;

import com.rentatools.shared.domain.Ids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador tipado del agregado {@link Invoice}.
 */
public record InvoiceId(UUID value) {

    public InvoiceId {
        Objects.requireNonNull(value, "InvoiceId value must not be null");
    }

    public static InvoiceId of(UUID value) {
        return new InvoiceId(value);
    }

    public static InvoiceId newId() {
        return new InvoiceId(Ids.newId());
    }
}
