package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.CreateChecklistItemCommand;
import com.rentatools.rental.application.dto.UpdateChecklistItemCommand;
import jakarta.validation.constraints.NotBlank;

public final class ChecklistItemRequest {

    private ChecklistItemRequest() {
    }

    public record Create(
            @NotBlank String code,
            @NotBlank String label
    ) {
        public CreateChecklistItemCommand toCommand() {
            return new CreateChecklistItemCommand(code, label);
        }
    }

    public record Update(
            @NotBlank String label,
            boolean active
    ) {
        public UpdateChecklistItemCommand toCommand() {
            return new UpdateChecklistItemCommand(label, active);
        }
    }
}
