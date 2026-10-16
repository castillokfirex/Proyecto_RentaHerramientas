package com.rentatools.billing.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceLine;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.billing.domain.model.invoice.Payment;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.InvoiceLineJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity;
import com.rentatools.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mapper bidireccional entre el agregado {@link Invoice} y su grafo de entidades JPA.
 */
@Component
public class InvoicePersistenceMapper {

    public InvoiceJpaEntity toNewEntity(Invoice invoice) {
        InvoiceJpaEntity entity = new InvoiceJpaEntity();
        entity.setId(invoice.id().value());
        entity.setReservationId(invoice.reservationId());
        entity.setType(invoice.type());
        entity.setNumber(invoice.number() != null ? invoice.number().value() : null);
        entity.setTaxRate(invoice.taxRate());
        entity.setIssuedAt(invoice.issuedAt());
        entity.setVoidedAt(invoice.voidedAt());
        entity.setCreatedAt(invoice.createdAt());

        for (InvoiceLine line : invoice.lines()) {
            entity.getLines().add(toLineEntity(line, entity));
        }
        for (Payment payment : invoice.payments()) {
            entity.getPayments().add(toPaymentEntity(payment, entity));
        }
        return entity;
    }

    public InvoiceJpaEntity updateEntity(InvoiceJpaEntity existing, Invoice invoice) {
        existing.setNumber(invoice.number() != null ? invoice.number().value() : null);
        existing.setTaxRate(invoice.taxRate());
        existing.setIssuedAt(invoice.issuedAt());
        existing.setVoidedAt(invoice.voidedAt());

        Map<UUID, InvoiceLineJpaEntity> existingLinesById = existing.getLines().stream()
                .collect(Collectors.toMap(InvoiceLineJpaEntity::getId, Function.identity()));
        existing.getLines().removeIf(lineEntity ->
                invoice.lines().stream().noneMatch(l -> l.id().equals(lineEntity.getId()))
        );
        for (InvoiceLine line : invoice.lines()) {
            InvoiceLineJpaEntity lineEntity = existingLinesById.get(line.id());
            if (lineEntity == null) {
                existing.getLines().add(toLineEntity(line, existing));
            } else {
                lineEntity.setReservationUnitId(line.reservationUnitId());
                lineEntity.setConcept(line.concept());
                lineEntity.setDescription(line.description());
                lineEntity.setQuantity(line.quantity());
                lineEntity.setUnitPrice(line.unitPrice().amount());
            }
        }

        Map<UUID, PaymentJpaEntity> existingPaymentsById = existing.getPayments().stream()
                .collect(Collectors.toMap(PaymentJpaEntity::getId, Function.identity()));
        for (Payment payment : invoice.payments()) {
            if (!existingPaymentsById.containsKey(payment.id())) {
                existing.getPayments().add(toPaymentEntity(payment, existing));
            }
        }

        return existing;
    }

    public Invoice toDomain(InvoiceJpaEntity entity) {
        List<InvoiceLine> lines = entity.getLines().stream()
                .map(l -> new InvoiceLine(
                        l.getId(),
                        l.getReservationUnitId(),
                        l.getConcept(),
                        l.getDescription(),
                        l.getQuantity(),
                        Money.of(l.getUnitPrice())
                ))
                .toList();

        List<Payment> payments = entity.getPayments().stream()
                .map(p -> new Payment(
                        p.getId(),
                        p.getKind(),
                        Money.of(p.getAmount()),
                        p.getMethod(),
                        p.getReference(),
                        p.getOccurredAt(),
                        p.getRecordedBy()
                ))
                .toList();

        return Invoice.reconstitute(
                InvoiceId.of(entity.getId()),
                entity.getReservationId(),
                entity.getType(),
                entity.getNumber() != null ? InvoiceNumber.of(entity.getNumber()) : null,
                entity.getTaxRate(),
                entity.getIssuedAt(),
                entity.getVoidedAt(),
                entity.getCreatedAt(),
                lines,
                payments,
                entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    private static InvoiceLineJpaEntity toLineEntity(InvoiceLine line, InvoiceJpaEntity parent) {
        InvoiceLineJpaEntity e = new InvoiceLineJpaEntity();
        e.setId(line.id());
        e.setInvoice(parent);
        e.setReservationUnitId(line.reservationUnitId());
        e.setConcept(line.concept());
        e.setDescription(line.description());
        e.setQuantity(line.quantity());
        e.setUnitPrice(line.unitPrice().amount());
        return e;
    }

    private static PaymentJpaEntity toPaymentEntity(Payment payment, InvoiceJpaEntity parent) {
        PaymentJpaEntity e = new PaymentJpaEntity();
        e.setId(payment.id());
        e.setInvoice(parent);
        e.setKind(payment.kind());
        e.setAmount(payment.amount().amount());
        e.setMethod(payment.method());
        e.setReference(payment.reference());
        e.setOccurredAt(payment.occurredAt());
        e.setRecordedBy(payment.recordedBy());
        return e;
    }
}
