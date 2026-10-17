package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.domain.model.pricing.Quote;
import com.rentatools.rental.domain.model.pricing.QuoteLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta JSON para la cotización de una reserva.
 */
public record QuoteResponse(
        LocalDate startDate,
        LocalDate endDate,
        long days,
        List<LineResponse> lines,
        BigDecimal totalAgreedAmount,
        BigDecimal totalDepositAmount
) {

    public record LineResponse(
            UUID toolId,
            int quantity,
            BigDecimal dailyRate,
            BigDecimal unitAgreedAmount,
            BigDecimal unitDepositAmount,
            BigDecimal lineAgreedAmount,
            BigDecimal lineDepositAmount
    ) {
        public static LineResponse from(QuoteLine line) {
            return new LineResponse(
                    line.toolId(),
                    line.quantity(),
                    line.dailyRate().amount(),
                    line.unitAgreedAmount().amount(),
                    line.unitDepositAmount().amount(),
                    line.lineAgreedAmount().amount(),
                    line.lineDepositAmount().amount()
            );
        }
    }

    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(
                quote.dateRange().start(),
                quote.dateRange().end(),
                quote.days(),
                quote.lines().stream().map(LineResponse::from).toList(),
                quote.totalAgreedAmount().amount(),
                quote.totalDepositAmount().amount()
        );
    }
}
