package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.InvoiceTotals;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO de lectura de una factura con su estado derivado, líneas, pagos y totales calculados.
 */
public record InvoiceView(
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

    public static InvoiceView from(Invoice invoice) {
        return from(invoice, Money.ZERO);
    }

    public static InvoiceView from(Invoice invoice, Money retainedDeposit) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        Money retained = (retainedDeposit == null) ? Money.ZERO : retainedDeposit;
        InvoiceTotals totals = invoice.calculateTotals(retained);

        List<InvoiceLineView> lineViews = invoice.lines().stream()
                .map(InvoiceLineView::from)
                .toList();
        List<PaymentView> paymentViews = invoice.payments().stream()
                .map(PaymentView::from)
                .toList();

        return new InvoiceView(
                invoice.id().value(),
                invoice.reservationId(),
                invoice.type(),
                invoice.status(),
                invoice.number() != null ? invoice.number().value() : null,
                invoice.taxRate(),
                invoice.issuedAt(),
                invoice.voidedAt(),
                invoice.createdAt(),
                lineViews,
                paymentViews,
                totals.subtotal().amount(),
                totals.tax().amount(),
                totals.total().amount(),
                totals.netPaid().amount(),
                totals.retainedDeposit().amount(),
                totals.balance().amount(),
                totals.isPaid()
        );
    }
}
