package com.rentatools.rental.application.port.out;

import com.rentatools.rental.application.dto.FrequentClientReportView;
import com.rentatools.rental.application.dto.ToolStatusReportView;
import com.rentatools.rental.application.dto.TopToolReportView;
import java.util.List;

/**
 * Puerto de salida para la obtención de métricas y reportes analíticos de rental e inventario.
 */
public interface RentalReportPort {

    List<TopToolReportView> findTopTools(int limit);

    List<FrequentClientReportView> findFrequentClients(int limit);

    ToolStatusReportView getToolStatusSummary();
}
