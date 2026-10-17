package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import com.rentatools.billing.application.dto.InvoiceLineView;
import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.dto.PaymentView;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.InvoiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta HTTP con el detalle completo de una factura.
 */
public record InvoiceResponse(
        UUID id,
        UUID reservationId,
        InvoiceType type,
        InvoiceStatus status,
        Long number,
        BigDecimal taxRate,
        Instant issuedAt,
        Instant voidedAt,
        Instant createdAt,
        List<InvoiceLineView> lines,
        List<PaymentView> payments,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal netPaid,
        BigDecimal retainedDeposit,
        BigDecimal balance,
        boolean paid
) {

    public static InvoiceResponse from(InvoiceView view) {
        if (view == null) {
            return null;
        }
        return new InvoiceResponse(
                view.id(),
                view.reservationId(),
                view.type(),
                view.status(),
                view.number(),
                view.taxRate(),
                view.issuedAt(),
                view.voidedAt(),
                view.createdAt(),
                view.lines(),
                view.payments(),
                view.subtotal(),
                view.tax(),
                view.total(),
                view.netPaid(),
                view.retainedDeposit(),
                view.balance(),
                view.paid()
        );
    }
}
