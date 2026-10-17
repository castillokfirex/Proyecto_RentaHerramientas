package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import com.rentatools.billing.application.dto.DepositTransactionView;
import com.rentatools.billing.application.dto.RetentionView;
import com.rentatools.billing.application.dto.SecurityDepositView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta HTTP con el estado y movimientos del depósito de garantía de una reserva.
 */
public record SecurityDepositResponse(
        UUID id,
        UUID reservationId,
        BigDecimal requiredAmount,
        BigDecimal totalCollected,
        BigDecimal totalRefunded,
        BigDecimal totalRetained,
        BigDecimal balance,
        boolean fullyCollected,
        boolean settled,
        Instant createdAt,
        Instant settledAt,
        List<DepositTransactionView> transactions,
        List<RetentionView> retentions
) {

    public static SecurityDepositResponse from(SecurityDepositView view) {
        if (view == null) {
            return null;
        }
        return new SecurityDepositResponse(
                view.id(),
                view.reservationId(),
                view.requiredAmount(),
                view.totalCollected(),
                view.totalRefunded(),
                view.totalRetained(),
                view.balance(),
                view.fullyCollected(),
                view.settled(),
                view.createdAt(),
                view.settledAt(),
                view.transactions(),
                view.retentions()
        );
    }
}
