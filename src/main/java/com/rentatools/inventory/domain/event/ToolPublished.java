package com.rentatools.inventory.domain.event;

import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio emitido cuando una herramienta pasa a estado {@code ACTIVE}.
 */
public record ToolPublished(
        UUID eventId,
        Instant occurredAt,
        ToolId toolId,
        ProviderId providerId
) implements DomainEvent {

    public ToolPublished {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
    }
}
