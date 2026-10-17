package com.rentatools.billing.infrastructure.adapter.in.web;

import com.rentatools.billing.application.port.in.GetRevenueReportQuery;
import com.rentatools.billing.infrastructure.adapter.in.web.dto.RevenueReportResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Controlador REST de reportes y métricas financieras para administración (rol {@code ADMIN}).
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingReportController {

    private final GetRevenueReportQuery getRevenueReportQuery;

    public AdminBillingReportController(GetRevenueReportQuery getRevenueReportQuery) {
        this.getRevenueReportQuery = getRevenueReportQuery;
    }

    @GetMapping("/revenue")
    public RevenueReportResponse getRevenueReport(
            @CurrentActor Actor actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return RevenueReportResponse.from(
                getRevenueReportQuery.getRevenueReport(actor, from, to)
        );
    }
}
