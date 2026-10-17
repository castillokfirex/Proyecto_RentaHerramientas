package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.tool.ToolRate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para definir o reemplazar las tarifas de alquiler ({@code DAY}, {@code WEEK}, {@code MONTH}) de una herramienta.
 */
public record SetToolRatesCommand(
        UUID toolId,
        List<ToolRate> rates
) {
    public SetToolRatesCommand {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(rates, "rates must not be null");
        rates = List.copyOf(rates);
    }
}
