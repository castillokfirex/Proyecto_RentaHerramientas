package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.FrequentClientReportView;
import com.rentatools.rental.application.dto.ToolStatusReportView;
import com.rentatools.rental.application.dto.TopToolReportView;
import com.rentatools.rental.application.port.in.GetRentalReportsQuery;
import com.rentatools.rental.application.port.out.RentalReportPort;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicación para consultar reportes analíticos de gestión y auditoría (solo {@code ADMIN}).
 */
public class RentalReportService implements GetRentalReportsQuery {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final RentalReportPort rentalReportPort;

    public RentalReportService(RentalReportPort rentalReportPort) {
        this.rentalReportPort = Objects.requireNonNull(rentalReportPort, "rentalReportPort must not be null");
    }

    @Override
    public List<TopToolReportView> getTopTools(Actor actor, int limit) {
        Objects.requireNonNull(actor, "actor must not be null");
        assertAdmin(actor);
        return rentalReportPort.findTopTools(normalizeLimit(limit));
    }

    @Override
    public List<FrequentClientReportView> getFrequentClients(Actor actor, int limit) {
        Objects.requireNonNull(actor, "actor must not be null");
        assertAdmin(actor);
        return rentalReportPort.findFrequentClients(normalizeLimit(limit));
    }

    @Override
    public ToolStatusReportView getToolStatusSummary(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        assertAdmin(actor);
        return rentalReportPort.getToolStatusSummary();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void assertAdmin(Actor actor) {
        if (!actor.is(ActorRole.ADMIN)) {
            throw new BusinessRuleViolation("FORBIDDEN", "Only administrators can access management reports");
        }
    }
}
