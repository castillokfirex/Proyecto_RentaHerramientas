package com.rentatools.rental.domain.exception;

import com.rentatools.shared.domain.DomainException;
import java.util.UUID;

/**
 * Se lanza cuando no existen suficientes unidades disponibles para cubrir la cantidad solicitada.
 */
public final class InsufficientAvailabilityException extends DomainException {

    private final UUID toolId;
    private final int requested;
    private final int available;

    public InsufficientAvailabilityException(UUID toolId, int requested, int available) {
        super(
                "INSUFFICIENT_AVAILABILITY",
                "Insufficient available units for tool " + toolId + ": requested " + requested + ", available " + available
        );
        this.toolId = toolId;
        this.requested = requested;
        this.available = available;
    }

    public InsufficientAvailabilityException(String message) {
        super("INSUFFICIENT_AVAILABILITY", message);
        this.toolId = null;
        this.requested = 0;
        this.available = 0;
    }

    public UUID toolId() {
        return toolId;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
