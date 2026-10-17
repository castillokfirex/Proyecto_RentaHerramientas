package com.rentatools.rental.infrastructure.adapter.in.web;

import com.rentatools.rental.application.port.in.GetRentalReportsQuery;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.FrequentClientReportResponse;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ToolStatusReportResponse;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.TopToolReportResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de reportes de administración para el contexto de alquileres e inventario (solo {@code ADMIN}).
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRentalReportController {

    private final GetRentalReportsQuery getRentalReportsQuery;

    public AdminRentalReportController(GetRentalReportsQuery getRentalReportsQuery) {
        this.getRentalReportsQuery = Objects.requireNonNull(getRentalReportsQuery, "getRentalReportsQuery must not be null");
    }

    @GetMapping("/top-tools")
    public List<TopToolReportResponse> getTopTools(
            @CurrentActor Actor actor,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return getRentalReportsQuery.getTopTools(actor, limit).stream()
                .map(TopToolReportResponse::from)
                .toList();
    }

    @GetMapping("/frequent-clients")
    public List<FrequentClientReportResponse> getFrequentClients(
            @CurrentActor Actor actor,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return getRentalReportsQuery.getFrequentClients(actor, limit).stream()
                .map(FrequentClientReportResponse::from)
                .toList();
    }

    @GetMapping("/tool-status")
    public ToolStatusReportResponse getToolStatusSummary(
            @CurrentActor Actor actor
    ) {
        return ToolStatusReportResponse.from(getRentalReportsQuery.getToolStatusSummary(actor));
    }
}
