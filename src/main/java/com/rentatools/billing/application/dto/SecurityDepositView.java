package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.deposit.SecurityDeposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO de lectura para el depósito de garantía de una reserva con su saldo derivado.
 */
public record SecurityDepositView(
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

    public static SecurityDepositView from(SecurityDeposit deposit) {
        Objects.requireNonNull(deposit, "deposit must not be null");
        List<DepositTransactionView> txViews = deposit.transactions().stream()
                .map(DepositTransactionView::from)
                .toList();
        List<RetentionView> retentionViews = deposit.retentions().stream()
                .map(RetentionView::from)
                .toList();

        return new SecurityDepositView(
                deposit.id().value(),
                deposit.reservationId(),
                deposit.requiredAmount().amount(),
                deposit.totalCollected().amount(),
                deposit.totalRefunded().amount(),
                deposit.totalRetained().amount(),
                deposit.balance().amount(),
                deposit.isFullyCollected(),
                deposit.isSettled(),
                deposit.createdAt(),
                deposit.settledAt(),
                txViews,
                retentionViews
        );
    }
}
