package com.rentatools.iam.domain.model;

import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para la identificación tributaria (NIT / RUC / Tax ID) de un proveedor.
 */
public record TaxId(String value) {

    private static final Pattern TAX_ID_PATTERN = Pattern.compile("^[A-Z0-9-]{5,20}$");

    public TaxId {
        Objects.requireNonNull(value, "taxId must not be null");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TAX_ID_PATTERN.matcher(normalized).matches()) {
            throw new BusinessRuleViolation("INVALID_TAX_ID", "Invalid tax ID format: " + value);
        }
        value = normalized;
    }

    public static TaxId of(String value) {
        return new TaxId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
