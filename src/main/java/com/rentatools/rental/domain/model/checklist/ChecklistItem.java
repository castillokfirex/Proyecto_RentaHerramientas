package com.rentatools.rental.domain.model.checklist;

import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.Locale;
import java.util.Objects;

/**
 * Raíz de agregado que representa un ítem del catálogo de checklist de inspección (`checklist_items`).
 */
public final class ChecklistItem extends AggregateRoot {

    private static final int MAX_CODE_LENGTH = 40;
    private static final int MAX_LABEL_LENGTH = 120;

    private final ChecklistItemId id;
    private final String code;
    private String label;
    private boolean active;

    private ChecklistItem(ChecklistItemId id, String code, String label, boolean active) {
        this.id = id;
        this.code = validateCode(code);
        this.label = validateLabel(label);
        this.active = active;
    }

    public static ChecklistItem create(ChecklistItemId id, String code, String label) {
        return new ChecklistItem(id, code, label, true);
    }

    public static ChecklistItem create(String code, String label) {
        return new ChecklistItem(null, code, label, true);
    }

    public static ChecklistItem reconstruct(ChecklistItemId id, String code, String label, boolean active) {
        Objects.requireNonNull(id, "id must not be null");
        return new ChecklistItem(id, code, label, active);
    }

    public void update(String label, boolean active) {
        this.label = validateLabel(label);
        this.active = active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public ChecklistItemId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    private static String validateCode(String code) {
        Objects.requireNonNull(code, "code must not be null");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new BusinessRuleViolation("INVALID_CHECKLIST_CODE", "Checklist item code cannot be blank");
        }
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new BusinessRuleViolation(
                    "INVALID_CHECKLIST_CODE",
                    "Checklist item code cannot exceed " + MAX_CODE_LENGTH + " characters"
            );
        }
        return normalized;
    }

    private static String validateLabel(String label) {
        Objects.requireNonNull(label, "label must not be null");
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolation("INVALID_CHECKLIST_LABEL", "Checklist item label cannot be blank");
        }
        if (trimmed.length() > MAX_LABEL_LENGTH) {
            throw new BusinessRuleViolation(
                    "INVALID_CHECKLIST_LABEL",
                    "Checklist item label cannot exceed " + MAX_LABEL_LENGTH + " characters"
            );
        }
        return trimmed;
    }
}
