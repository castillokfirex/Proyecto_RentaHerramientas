package com.rentatools.billing.infrastructure;

import com.rentatools.billing.application.port.out.InvoiceNumberAllocator;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.billing.infrastructure.adapter.out.persistence.InvoicePersistenceAdapter;
import com.rentatools.billing.infrastructure.adapter.out.persistence.MaxPlusOneInvoiceNumberAllocator;
import com.rentatools.billing.infrastructure.adapter.out.persistence.SecurityDepositPersistenceAdapter;
import com.rentatools.billing.infrastructure.adapter.out.persistence.mapper.InvoicePersistenceMapper;
import com.rentatools.billing.infrastructure.adapter.out.persistence.mapper.SecurityDepositPersistenceMapper;
import com.rentatools.billing.infrastructure.adapter.out.rental.RentalReservationParticipantsAdapter;
import com.rentatools.billing.infrastructure.config.BillingProperties;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.support.AbstractMySqlIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de persistencia JPA contra {@code V4__billing.sql} en MySQL 8.4 (Testcontainers),
 * incluyendo inserción en cascada, cálculo de saldos y numeración concurrente de facturas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        InvoicePersistenceAdapter.class,
        SecurityDepositPersistenceAdapter.class,
        MaxPlusOneInvoiceNumberAllocator.class,
        InvoicePersistenceMapper.class,
        SecurityDepositPersistenceMapper.class,
        RentalReservationParticipantsAdapter.class,
        BillingProperties.class
})
class BillingPersistenceIT extends AbstractMySqlIT {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private SecurityDepositRepository securityDepositRepository;

    @Autowired
    private InvoiceNumberAllocator invoiceNumberAllocator;

    @Autowired
    private ReservationParticipantsPort reservationParticipantsPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Persiste facturas, líneas en cascada, pagos, garantías y retenciones contra V4__billing.sql")
    void persistsInvoicesLinesPaymentsDepositsAndRetentionsWithCascades() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        FixtureIds fixture = seedReservationFixture("RES-IT-01", now);

        // Verificar que el adaptador de partícipes resuelve cliente y proveedor
        var participants = reservationParticipantsPort.findByReservationId(fixture.reservationId());
        assertThat(participants).isPresent();
        assertThat(participants.get().clientId()).isEqualTo(fixture.clientId());
        assertThat(participants.get().providerId()).isEqualTo(fixture.providerId());

        // 1) Crear y emitir factura RENTAL con línea y pago
        Invoice draftRental = Invoice.createDraft(
                fixture.reservationId(),
                InvoiceType.RENTAL,
                new BigDecimal("0.1900"),
                now
        );
        draftRental.addLine(fixture.reservationUnitId(), LineConcept.RENTAL, "Alquiler taladro", 1L, Money.of("100000"));
        Invoice issuedRental = invoiceNumberAllocator.issueAndSave(draftRental, now, invoiceRepository);

        assertThat(issuedRental.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issuedRental.number()).isNotNull();
        assertThat(issuedRental.total()).isEqualTo(Money.of("119000.00"));

        issuedRental.registerPayment(
                Money.of("119000.00"),
                PaymentMethod.SIMULATED,
                "PAY-IT-1",
                now.plusSeconds(5),
                fixture.clientId()
        );
        Invoice savedRental = invoiceRepository.save(issuedRental);
        assertThat(savedRental.balance()).isEqualTo(Money.ZERO);
        assertThat(savedRental.payments()).hasSize(1);

        // 2) Crear garantía, cobrarla, emitir SETTLEMENT con daño, aplicar retención y liquidar
        SecurityDeposit deposit = SecurityDeposit.open(fixture.reservationId(), Money.of("150000.00"), now);
        deposit.collect(Money.of("150000.00"), PaymentMethod.SIMULATED, "DEP-IT-1", now.plusSeconds(10), fixture.clientId());
        deposit = securityDepositRepository.save(deposit);
        assertThat(deposit.balance()).isEqualTo(Money.of("150000.00"));

        Invoice draftSettlement = Invoice.createDraft(
                fixture.reservationId(),
                InvoiceType.SETTLEMENT,
                new BigDecimal("0.1900"),
                now.plusSeconds(20)
        );
        draftSettlement.addLine(fixture.reservationUnitId(), LineConcept.DAMAGE, "Reparación", 1L, Money.of("50000.00"));
        Invoice issuedSettlement = invoiceNumberAllocator.issueAndSave(
                draftSettlement,
                now.plusSeconds(25),
                invoiceRepository
        );
        // Total SETTLEMENT = 50,000 + 9,500 = 59,500.00
        assertThat(issuedSettlement.total()).isEqualTo(Money.of("59500.00"));

        deposit.retain(issuedSettlement.id(), Money.of("59500.00"), now.plusSeconds(30));
        deposit.refund(
                Money.of("90500.00"),
                PaymentMethod.SIMULATED,
                "REF-IT-1",
                now.plusSeconds(30),
                fixture.providerId()
        );
        deposit.settle(now.plusSeconds(30));
        securityDepositRepository.save(deposit);

        SecurityDeposit reloadedDeposit = securityDepositRepository
                .findByReservationId(fixture.reservationId())
                .orElseThrow();
        assertThat(reloadedDeposit.isSettled()).isTrue();
        assertThat(reloadedDeposit.totalCollected()).isEqualTo(Money.of("150000.00"));
        assertThat(reloadedDeposit.totalRetained()).isEqualTo(Money.of("59500.00"));
        assertThat(reloadedDeposit.totalRefunded()).isEqualTo(Money.of("90500.00"));
        assertThat(reloadedDeposit.balance()).isEqualTo(Money.ZERO);
        assertThat(reloadedDeposit.retentions()).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Asigna números de factura secuenciales distintos cuando dos hilos emiten simultáneamente")
    void allocatesDistinctSequentialInvoiceNumbersUnderConcurrency() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        FixtureIds f1 = seedReservationFixture("RES-CONC-1", now);
        FixtureIds f2 = seedReservationFixture("RES-CONC-2", now);

        Invoice draft1 = Invoice.createDraft(f1.reservationId(), InvoiceType.RENTAL, new BigDecimal("0.1900"), now);
        draft1.addLine(f1.reservationUnitId(), LineConcept.RENTAL, "Unidad 1", 1L, Money.of("80000"));

        Invoice draft2 = Invoice.createDraft(f2.reservationId(), InvoiceType.RENTAL, new BigDecimal("0.1900"), now);
        draft2.addLine(f2.reservationUnitId(), LineConcept.RENTAL, "Unidad 2", 1L, Money.of("90000"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<InvoiceNumber> task1 = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return invoiceNumberAllocator.issueAndSave(draft1, now, invoiceRepository).number();
            };
            Callable<InvoiceNumber> task2 = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return invoiceNumberAllocator.issueAndSave(draft2, now, invoiceRepository).number();
            };

            Future<InvoiceNumber> future1 = pool.submit(task1);
            Future<InvoiceNumber> future2 = pool.submit(task2);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            InvoiceNumber num1 = future1.get(10, TimeUnit.SECONDS);
            InvoiceNumber num2 = future2.get(10, TimeUnit.SECONDS);

            assertThat(num1).isNotNull();
            assertThat(num2).isNotNull();
            assertThat(Set.of(num1.value(), num2.value())).hasSize(2);
            assertThat(Math.abs(num1.value() - num2.value())).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            cleanupReservationFixture(f1);
            cleanupReservationFixture(f2);
        }
    }

    private FixtureIds seedReservationFixture(String reservationCode, Instant now) {
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();
        UUID toolId = Ids.newId();
        UUID unitId = Ids.newId();
        UUID reservationId = Ids.newId();
        UUID reservationUnitId = Ids.newId();
        Timestamp ts = Timestamp.from(now);

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)",
                toBytes(clientId), "client-" + reservationCode + "@test.com", "hash", "Cliente Test", ts, ts
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)",
                toBytes(providerId), "provider-" + reservationCode + "@test.com", "hash", "Proveedor Test", ts, ts
        );
        jdbcTemplate.update(
                "INSERT INTO tools (id, provider_id, name, deposit_amount, status, created_at, updated_at, version) VALUES (?, ?, ?, 50000.00, 'ACTIVE', ?, ?, 0)",
                toBytes(toolId), toBytes(providerId), "Taladro " + reservationCode, ts, ts
        );
        jdbcTemplate.update(
                "INSERT INTO tool_units (id, tool_id, serial_number, status, created_at) VALUES (?, ?, ?, 'AVAILABLE', ?)",
                toBytes(unitId), toBytes(toolId), "SN-" + reservationCode, ts
        );
        jdbcTemplate.update(
                "INSERT INTO reservations (id, code, client_id, provider_id, start_date, end_date, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?, 0)",
                toBytes(reservationId),
                reservationCode,
                toBytes(clientId),
                toBytes(providerId),
                Date.valueOf(LocalDate.of(2026, 6, 10)),
                Date.valueOf(LocalDate.of(2026, 6, 12)),
                ts,
                ts
        );
        jdbcTemplate.update(
                "INSERT INTO reservation_units (id, reservation_id, unit_id, daily_rate, agreed_amount, deposit_amount) VALUES (?, ?, ?, 30000.00, 90000.00, 50000.00)",
                toBytes(reservationUnitId), toBytes(reservationId), toBytes(unitId)
        );

        return new FixtureIds(clientId, providerId, toolId, unitId, reservationId, reservationUnitId);
    }

    private void cleanupReservationFixture(FixtureIds f) {
        List<Invoice> invoices = invoiceRepository.findByReservationId(f.reservationId());
        for (Invoice inv : invoices) {
            invoiceRepository.delete(inv.id());
        }
        jdbcTemplate.update("DELETE FROM reservations WHERE id = ?", (Object) toBytes(f.reservationId()));
        jdbcTemplate.update("DELETE FROM tool_units WHERE id = ?", (Object) toBytes(f.unitId()));
        jdbcTemplate.update("DELETE FROM tools WHERE id = ?", (Object) toBytes(f.toolId()));
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", toBytes(f.clientId()), toBytes(f.providerId()));
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private record FixtureIds(
            UUID clientId,
            UUID providerId,
            UUID toolId,
            UUID unitId,
            UUID reservationId,
            UUID reservationUnitId
    ) {
    }
}
