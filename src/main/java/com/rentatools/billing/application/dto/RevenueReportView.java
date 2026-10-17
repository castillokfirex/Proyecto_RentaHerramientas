package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.invoice.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Vista de reporte financiero para administración con el resumen de facturación,
 * recaudación, garantías retenidas/devueltas y desglose por medio de pago.
 */
public record RevenueReportView(
        BigDecimal totalInvoiced,
        BigDecimal totalCollected,
        BigDecimal depositsRetained,
        BigDecimal depositsRefunded,
        Map<PaymentMethod, BigDecimal> breakdownByPaymentMethod
) {

    public RevenueReportView {
        Objects.requireNonNull(totalInvoiced, "totalInvoiced must not be null");
        Objects.requireNonNull(totalCollected, "totalCollected must not be null");
        Objects.requireNonNull(depositsRetained, "depositsRetained must not be null");
        Objects.requireNonNull(depositsRefunded, "depositsRefunded must not be null");
        Objects.requireNonNull(breakdownByPaymentMethod, "breakdownByPaymentMethod must not be null");
        breakdownByPaymentMethod = Map.copyOf(breakdownByPaymentMethod);
    }
}
