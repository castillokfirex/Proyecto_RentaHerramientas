package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.ChecklistItemView;

public record ChecklistItemResponse(
        int id,
        String code,
        String label,
        boolean active
) {
    public static ChecklistItemResponse from(ChecklistItemView view) {
        return new ChecklistItemResponse(
                view.id(),
                view.code(),
                view.label(),
                view.active()
        );
    }
}
