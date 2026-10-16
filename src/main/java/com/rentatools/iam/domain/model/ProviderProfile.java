package com.rentatools.iam.domain.model;

import com.rentatools.shared.domain.BusinessRuleViolation;
import java.time.Instant;
import java.util.Objects;

/**
 * Perfil de proveedor asociado a un {@link User} con rol {@code PROVEEDOR}.
 * El estado de aprobación se deriva de {@code approvedAt != null} (cumpliendo 3FN/FNBC según DDL-4FN.md).
 */
public record ProviderProfile(
        String businessName,
        TaxId taxId,
        Instant approvedAt,
        UserId approvedBy
) {

    private static final int MAX_BUSINESS_NAME_LENGTH = 150;

    public ProviderProfile {
        Objects.requireNonNull(businessName, "businessName must not be null");
        Objects.requireNonNull(taxId, "taxId must not be null");
        String trimmedName = businessName.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > MAX_BUSINESS_NAME_LENGTH) {
            throw new BusinessRuleViolation(
                    "INVALID_BUSINESS_NAME",
                    "businessName must not be blank and must have at most " + MAX_BUSINESS_NAME_LENGTH + " characters"
            );
        }
        if ((approvedAt == null) != (approvedBy == null)) {
            throw new BusinessRuleViolation(
                    "INVALID_PROVIDER_APPROVAL_STATE",
                    "approvedAt and approvedBy must both be null or both be non-null"
            );
        }
        businessName = trimmedName;
    }

    public static ProviderProfile pending(String businessName, TaxId taxId) {
        return new ProviderProfile(businessName, taxId, null, null);
    }

    public boolean isApproved() {
        return approvedAt != null;
    }

    public ProviderProfile approve(UserId adminId, Instant now) {
        Objects.requireNonNull(adminId, "adminId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (isApproved()) {
            throw new BusinessRuleViolation("PROVIDER_ALREADY_APPROVED", "Provider is already approved");
        }
        return new ProviderProfile(businessName, taxId, now, adminId);
    }
}
