package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.FrequentClientReportView;
import com.rentatools.rental.application.dto.ToolStatusReportView;
import com.rentatools.rental.application.dto.TopToolReportView;
import com.rentatools.shared.domain.Actor;
import java.util.List;

/**
 * Consulta de reportes y analítica de administración para el contexto de alquileres e inventario.
 */
public interface GetRentalReportsQuery {

    List<TopToolReportView> getTopTools(Actor actor, int limit);

    List<FrequentClientReportView> getFrequentClients(Actor actor, int limit);

    ToolStatusReportView getToolStatusSummary(Actor actor);
}
