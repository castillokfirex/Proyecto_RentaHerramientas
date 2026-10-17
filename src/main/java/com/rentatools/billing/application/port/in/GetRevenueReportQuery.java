package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.RevenueReportView;
import com.rentatools.shared.domain.Actor;

import java.time.LocalDate;

/**
 * Consulta de reporte financiero de ingresos, cobros, garantías y desglose por método de pago (solo {@code ADMIN}).
 */
public interface GetRevenueReportQuery {

    RevenueReportView getRevenueReport(Actor actor, LocalDate from, LocalDate to);

    default RevenueReportView getRevenueReport(Actor actor) {
        return getRevenueReport(actor, null, null);
    }
}
