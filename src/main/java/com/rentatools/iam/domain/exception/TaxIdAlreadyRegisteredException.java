package com.rentatools.iam.domain.exception;

import com.rentatools.iam.domain.model.TaxId;
import com.rentatools.shared.domain.DomainException;

/**
 * Excepción lanzada cuando se intenta registrar un proveedor con un TaxId ya existente.
 */
public class TaxIdAlreadyRegisteredException extends DomainException {

    public TaxIdAlreadyRegisteredException(TaxId taxId) {
        super("TAX_ID_ALREADY_REGISTERED", "Tax ID is already registered: " + taxId.value());
    }
}
