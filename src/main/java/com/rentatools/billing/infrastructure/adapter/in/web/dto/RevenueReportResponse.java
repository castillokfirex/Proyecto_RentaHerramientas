package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import com.rentatools.billing.application.dto.RevenueReportView;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Respuesta HTTP para el endpoint de reportes financieros de administración ({@code GET /api/v1/admin/reports/revenue}).
 */
public record RevenueReportResponse(
        BigDecimal totalInvoiced,
        BigDecimal totalCollected,
        BigDecimal depositsRetained,
        BigDecimal depositsRefunded,
        Map<PaymentMethod, BigDecimal> breakdownByPaymentMethod
) {

    public static RevenueReportResponse from(RevenueReportView view) {
        return new RevenueReportResponse(
                view.totalInvoiced(),
                view.totalCollected(),
                view.depositsRetained(),
                view.depositsRefunded(),
                view.breakdownByPaymentMethod()
        );
    }
}
