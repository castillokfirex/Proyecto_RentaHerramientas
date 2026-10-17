package com.rentatools.inventory.domain.model.unit;

import com.rentatools.inventory.domain.event.UnitStatusChanged;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Agregado raíz que representa una unidad física de una herramienta ({@code tool_units}).
 *
 * <p>Invariantes:
 * <ul>
 *   <li>El número de serie es único por herramienta (validado al registrar).</li>
 *   <li>Los estados son {@code AVAILABLE}, {@code MAINTENANCE} y {@code RETIRED} (sin estado {@code RENTED}).</li>
 *   <li>El estado {@code RETIRED} es terminal: no se puede volver a {@code AVAILABLE} ni {@code MAINTENANCE}.</li>
 * </ul>
 */
public final class ToolUnit extends AggregateRoot {

    private static final int MAX_NOTES_LENGTH = 500;

    private final ToolUnitId id;
    private final ToolId toolId;
    private final SerialNumber serialNumber;
    private UnitStatus status;
    private final LocalDate acquiredOn;
    private String notes;
    private final Instant createdAt;

    private ToolUnit(
            ToolUnitId id,
            ToolId toolId,
            SerialNumber serialNumber,
            UnitStatus status,
            LocalDate acquiredOn,
            String notes,
            Instant createdAt,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "ToolUnit id must not be null");
        this.toolId = Objects.requireNonNull(toolId, "toolId must not be null");
        this.serialNumber = Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.acquiredOn = acquiredOn;
        this.notes = normalizeAndValidateNotes(notes);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Registra una nueva unidad física en estado {@link UnitStatus#AVAILABLE}.
     */
    public static ToolUnit register(
            ToolUnitId id,
            ToolId toolId,
            SerialNumber serialNumber,
            LocalDate acquiredOn,
            String notes,
            Instant now
    ) {
        return new ToolUnit(id, toolId, serialNumber, UnitStatus.AVAILABLE, acquiredOn, notes, now, 0L);
    }

    /**
     * Rehidrata una unidad física desde la capa de persistencia.
     */
    public static ToolUnit rehydrate(
            ToolUnitId id,
            ToolId toolId,
            SerialNumber serialNumber,
            UnitStatus status,
            LocalDate acquiredOn,
            String notes,
            Instant createdAt,
            long version
    ) {
        return new ToolUnit(id, toolId, serialNumber, status, acquiredOn, notes, createdAt, version);
    }

    /**
     * Cambia el estado operativo de la unidad física aplicando la máquina de estados
     * {@code AVAILABLE <-> MAINTENANCE -> RETIRED} (terminal) y emitiendo {@link UnitStatusChanged}.
     */
    public void changeStatus(UnitStatus newStatus, String updatedNotes, Instant now) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (this.status == UnitStatus.RETIRED && newStatus != UnitStatus.RETIRED) {
            throw new BusinessRuleViolation(
                    "UNIT_ALREADY_RETIRED",
                    "ToolUnit " + this.id + " is RETIRED (terminal state) and cannot transition to " + newStatus
            );
        }

        if (updatedNotes != null) {
            this.notes = normalizeAndValidateNotes(updatedNotes);
        }

        if (this.status == newStatus) {
            return;
        }

        UnitStatus previous = this.status;
        this.status = newStatus;
        registerEvent(new UnitStatusChanged(Ids.newId(), now, this.id, this.toolId, previous, newStatus));
    }

    private static String normalizeAndValidateNotes(String rawNotes) {
        if (rawNotes == null) {
            return null;
        }
        String trimmed = rawNotes.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Notes must not exceed " + MAX_NOTES_LENGTH + " characters");
        }
        return trimmed;
    }

    public ToolUnitId id() {
        return id;
    }

    public ToolId toolId() {
        return toolId;
    }

    public SerialNumber serialNumber() {
        return serialNumber;
    }

    public UnitStatus status() {
        return status;
    }

    public LocalDate acquiredOn() {
        return acquiredOn;
    }

    public String notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
