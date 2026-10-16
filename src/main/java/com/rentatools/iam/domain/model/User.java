package com.rentatools.iam.domain.model;

import com.rentatools.iam.domain.event.ProviderApproved;
import com.rentatools.iam.domain.event.UserRegistered;
import com.rentatools.iam.domain.exception.UserSuspendedException;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Raíz de agregado que representa a un usuario de la plataforma RentaTools.
 */
public final class User extends AggregateRoot {

    private static final int MAX_FULL_NAME_LENGTH = 150;
    private static final int MAX_PHONE_LENGTH = 20;

    private final UserId id;
    private Email email;
    private HashedPassword passwordHash;
    private String fullName;
    private String phone;
    private UserStatus status;
    private final Set<ActorRole> roles;
    private ProviderProfile providerProfile;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(
            UserId id,
            Email email,
            HashedPassword passwordHash,
            String fullName,
            String phone,
            UserStatus status,
            Set<ActorRole> roles,
            ProviderProfile providerProfile,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.fullName = validateFullName(fullName);
        this.phone = normalizePhone(phone);
        this.status = Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        if (roles.isEmpty()) {
            throw new BusinessRuleViolation("USER_ROLES_EMPTY", "User must have at least one role");
        }
        this.roles = EnumSet.copyOf(roles);
        this.providerProfile = providerProfile;
        validateProviderInvariant();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static User registerClient(
            UserId id,
            Email email,
            HashedPassword passwordHash,
            String fullName,
            String phone,
            Instant now
    ) {
        Set<ActorRole> roles = Set.of(ActorRole.CLIENTE);
        User user = new User(
                id,
                email,
                passwordHash,
                fullName,
                phone,
                UserStatus.ACTIVE,
                roles,
                null,
                now,
                now,
                0L
        );
        user.registerEvent(UserRegistered.of(id, email, roles, now));
        return user;
    }

    public static User registerProvider(
            UserId id,
            Email email,
            HashedPassword passwordHash,
            String fullName,
            String phone,
            String businessName,
            TaxId taxId,
            Instant now
    ) {
        Set<ActorRole> roles = Set.of(ActorRole.PROVEEDOR);
        ProviderProfile profile = ProviderProfile.pending(businessName, taxId);
        User user = new User(
                id,
                email,
                passwordHash,
                fullName,
                phone,
                UserStatus.ACTIVE,
                roles,
                profile,
                now,
                now,
                0L
        );
        user.registerEvent(UserRegistered.of(id, email, roles, now));
        return user;
    }

    public static User registerAdmin(
            UserId id,
            Email email,
            HashedPassword passwordHash,
            String fullName,
            String phone,
            Instant now
    ) {
        Set<ActorRole> roles = Set.of(ActorRole.ADMIN);
        User user = new User(
                id,
                email,
                passwordHash,
                fullName,
                phone,
                UserStatus.ACTIVE,
                roles,
                null,
                now,
                now,
                0L
        );
        user.registerEvent(UserRegistered.of(id, email, roles, now));
        return user;
    }

    public static User reconstitute(
            UserId id,
            Email email,
            HashedPassword passwordHash,
            String fullName,
            String phone,
            UserStatus status,
            Set<ActorRole> roles,
            ProviderProfile providerProfile,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new User(
                id,
                email,
                passwordHash,
                fullName,
                phone,
                status,
                roles,
                providerProfile,
                createdAt,
                updatedAt,
                version
        );
    }

    public void assertCanLogin() {
        if (this.status == UserStatus.SUSPENDED) {
            throw new UserSuspendedException(this.id);
        }
    }

    public void approveProvider(UserId adminId, Instant now) {
        Objects.requireNonNull(adminId, "adminId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!this.roles.contains(ActorRole.PROVEEDOR) || this.providerProfile == null) {
            throw new BusinessRuleViolation("USER_NOT_PROVIDER", "User is not a provider: " + this.id);
        }
        this.providerProfile = this.providerProfile.approve(adminId, now);
        this.updatedAt = now;
        registerEvent(ProviderApproved.of(this.id, adminId, now));
    }

    public void changeStatus(UserStatus newStatus, Instant now) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(now, "now must not be null");
        this.status = newStatus;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean hasRole(ActorRole role) {
        return this.roles.contains(role);
    }

    public boolean isActiveClient() {
        return isActive() && hasRole(ActorRole.CLIENTE);
    }

    public boolean isApprovedProvider() {
        return isActive()
                && hasRole(ActorRole.PROVEEDOR)
                && providerProfile != null
                && providerProfile.isApproved();
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public HashedPassword passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public String phone() {
        return phone;
    }

    public UserStatus status() {
        return status;
    }

    public Set<ActorRole> roles() {
        return Set.copyOf(roles);
    }

    public ProviderProfile providerProfile() {
        return providerProfile;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void validateProviderInvariant() {
        boolean isProvider = this.roles.contains(ActorRole.PROVEEDOR);
        if (isProvider && this.providerProfile == null) {
            throw new BusinessRuleViolation("PROVIDER_PROFILE_REQUIRED", "A PROVEEDOR user must have a ProviderProfile");
        }
        if (!isProvider && this.providerProfile != null) {
            throw new BusinessRuleViolation("UNEXPECTED_PROVIDER_PROFILE", "Only PROVEEDOR users may have a ProviderProfile");
        }
    }

    private static String validateFullName(String fullName) {
        Objects.requireNonNull(fullName, "fullName must not be null");
        String trimmed = fullName.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_FULL_NAME_LENGTH) {
            throw new BusinessRuleViolation(
                    "INVALID_FULL_NAME",
                    "fullName must not be blank and must have at most " + MAX_FULL_NAME_LENGTH + " characters"
            );
        }
        return trimmed;
    }

    private static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_PHONE_LENGTH) {
            throw new BusinessRuleViolation("INVALID_PHONE", "phone must have at most " + MAX_PHONE_LENGTH + " characters");
        }
        return trimmed;
    }
}
