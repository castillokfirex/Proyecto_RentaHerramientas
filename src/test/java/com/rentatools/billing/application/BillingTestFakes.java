package com.rentatools.billing.application;

import com.rentatools.billing.application.port.out.InvoiceNumberAllocator;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.DepositId;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.billing.domain.model.invoice.InvoiceType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fakes en memoria para las pruebas unitarias de la capa de aplicación de {@code billing}.
 */
public final class BillingTestFakes {

    private BillingTestFakes() {
    }

    public static final class InMemoryInvoiceRepository implements InvoiceRepository {

        private final Map<InvoiceId, Invoice> store = new LinkedHashMap<>();

        @Override
        public Invoice save(Invoice invoice) {
            Invoice copy = Invoice.reconstitute(
                    invoice.id(),
                    invoice.reservationId(),
                    invoice.type(),
                    invoice.number(),
                    invoice.taxRate(),
                    invoice.issuedAt(),
                    invoice.voidedAt(),
                    invoice.createdAt(),
                    invoice.lines(),
                    invoice.payments(),
                    invoice.version() + 1
            );
            store.put(copy.id(), copy);
            return copy;
        }

        @Override
        public Optional<Invoice> findById(InvoiceId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Invoice> findByReservationIdAndType(UUID reservationId, InvoiceType type) {
            return store.values().stream()
                    .filter(inv -> inv.reservationId().equals(reservationId) && inv.type() == type)
                    .findFirst();
        }

        @Override
        public List<Invoice> findByReservationId(UUID reservationId) {
            return store.values().stream()
                    .filter(inv -> inv.reservationId().equals(reservationId))
                    .toList();
        }

        @Override
        public List<Invoice> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public void delete(InvoiceId id) {
            store.remove(id);
        }
    }

    public static final class InMemorySecurityDepositRepository implements SecurityDepositRepository {

        private final Map<DepositId, SecurityDeposit> store = new LinkedHashMap<>();

        @Override
        public SecurityDeposit save(SecurityDeposit deposit) {
            SecurityDeposit copy = SecurityDeposit.reconstitute(
                    deposit.id(),
                    deposit.reservationId(),
                    deposit.requiredAmount(),
                    deposit.createdAt(),
                    deposit.settledAt(),
                    deposit.transactions(),
                    deposit.retentions(),
                    deposit.version() + 1
            );
            store.put(copy.id(), copy);
            return copy;
        }

        @Override
        public Optional<SecurityDeposit> findById(DepositId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<SecurityDeposit> findByReservationId(UUID reservationId) {
            return store.values().stream()
                    .filter(d -> d.reservationId().equals(reservationId))
                    .findFirst();
        }

        @Override
        public List<SecurityDeposit> findAll() {
            return List.copyOf(store.values());
        }
    }

    public static final class InMemoryInvoiceNumberAllocator implements InvoiceNumberAllocator {

        private final AtomicLong sequence = new AtomicLong(0L);

        @Override
        public InvoiceNumber allocateNext() {
            return InvoiceNumber.of(sequence.incrementAndGet());
        }
    }

    public static final class InMemoryReservationParticipantsPort implements ReservationParticipantsPort {

        private final Map<UUID, Participants> store = new LinkedHashMap<>();

        public void register(UUID reservationId, String code, UUID clientId, UUID providerId) {
            store.put(reservationId, new Participants(reservationId, code, clientId, providerId));
        }

        @Override
        public Optional<Participants> findByReservationId(UUID reservationId) {
            return Optional.ofNullable(store.get(reservationId));
        }
    }
}
