package com.rentatools.billing.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import com.rentatools.shared.integration.rental.ReservationReturned;
import com.rentatools.support.AbstractMySqlIT;
import com.rentatools.support.JwtRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración del flujo completo de {@code billing} ({@code PLAN.md} §9-4):
 * publica eventos sintéticos de {@code rental} con {@link ApplicationEventPublisher} y recorre
 * confirmación -> pago RENTAL + cobro de garantía -> {@link ReservationClearedForDelivery} ->
 * devolución con 2 días de mora -> SETTLEMENT en borrador -> cargo por daño -> liquidación ->
 * {@link ReservationSettled} + consulta de reporte financiero de ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BillingFlowIT.BillingEventsCaptureConfig.class)
class BillingFlowIT extends AbstractMySqlIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturedBillingEvents capturedEvents;

    @BeforeEach
    void clearEvents() {
        capturedEvents.clear();
    }

    @Test
    @DisplayName("Ciclo completo: ReservationConfirmed -> pago + garantía -> ReservationClearedForDelivery -> ReservationReturned (mora) -> daño -> liquidar -> ReservationSettled + reporte ADMIN")
    void completesFullBillingLifecycleReactingToRentalEventsAndEmittingBillingEvents() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();
        UUID toolId = Ids.newId();
        UUID unitId = Ids.newId();
        UUID reservationId = Ids.newId();
        UUID reservationUnitId = Ids.newId();

        seedDatabaseFixture(clientId, providerId, toolId, unitId, reservationId, reservationUnitId, now);

        // 1) Publicar evento sintético ReservationConfirmed:
        //    agreedAmount = 100,000.00 (con tax-rate 0.18 -> IVA 18,000.00, Total RENTAL = 118,000.00)
        //    depositAmount = 200,000.00
        applicationEventPublisher.publishEvent(new ReservationConfirmed(
                Ids.newId(),
                now,
                reservationId,
                "RES-FLOW-01",
                clientId,
                providerId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 12),
                providerId,
                List.of(new ReservationConfirmed.Line(
                        reservationUnitId,
                        "Rotomartillo Industrial",
                        "SN-FLOW-01",
                        new BigDecimal("100000.00"),
                        new BigDecimal("200000.00")
                ))
        ));

        // Consultar facturación de la reserva
        MvcResult getBillingResult = mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId)
                        .with(JwtRequestPostProcessors.asClient(clientId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalInvoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.rentalInvoice.total").value(118000.00))
                .andExpect(jsonPath("$.securityDeposit.requiredAmount").value(200000.00))
                .andReturn();

        JsonNode billingJson = objectMapper.readTree(getBillingResult.getResponse().getContentAsString());
        UUID rentalInvoiceId = UUID.fromString(billingJson.get("rentalInvoice").get("id").asText());

        // 2) Cliente paga la factura RENTAL (118,000.00) y cobra la garantía (200,000.00)
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/payments", rentalInvoiceId)
                        .with(JwtRequestPostProcessors.asClient(clientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 118000.00,
                                  "method": "SIMULATED",
                                  "reference": "PAY-RENTAL-FLOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true));

        assertThat(capturedEvents.clearedEvents()).isEmpty();

        mockMvc.perform(post("/api/v1/billing/reservations/{id}/deposit/collections", reservationId)
                        .with(JwtRequestPostProcessors.asClient(clientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 200000.00,
                                  "method": "SIMULATED",
                                  "reference": "DEP-COLLECT-FLOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyCollected").value(true));

        // Verificar que se emitió ReservationClearedForDelivery
        assertThat(capturedEvents.clearedEvents()).hasSize(1);
        assertThat(capturedEvents.clearedEvents().getFirst().reservationId()).isEqualTo(reservationId);

        // 3) Publicar evento sintético ReservationReturned con 2 días de mora y tarifa diaria 20,000.00
        //    Mora (late-fee-multiplier 1.5) = 2 * (20,000 * 1.5) = 60,000.00
        applicationEventPublisher.publishEvent(new ReservationReturned(
                Ids.newId(),
                now.plusSeconds(60),
                reservationId,
                providerId,
                List.of(new ReservationReturned.ReturnedUnit(
                        reservationUnitId,
                        "Rotomartillo Industrial",
                        LocalDate.of(2026, 6, 14),
                        2,
                        new BigDecimal("20000.00"),
                        true,
                        false
                ))
        ));

        MvcResult afterReturnResult = mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId)
                        .with(JwtRequestPostProcessors.asProvider(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementInvoice.status").value("DRAFT"))
                .andExpect(jsonPath("$.settlementInvoice.subtotal").value(60000.00))
                .andReturn();

        UUID settlementInvoiceId = UUID.fromString(
                objectMapper.readTree(afterReturnResult.getResponse().getContentAsString())
                        .get("settlementInvoice").get("id").asText()
        );

        // 4) Proveedor agrega cargo por daño de 40,000.00 -> Subtotal SETTLEMENT = 100,000.00, IVA 18% = 18,000.00, Total = 118,000.00
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/damage-charges", settlementInvoiceId)
                        .with(JwtRequestPostProcessors.asProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationUnitId": "%s",
                                  "description": "Reparación de empuñadura",
                                  "amount": 40000.00
                                }
                                """.formatted(reservationUnitId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(100000.00))
                .andExpect(jsonPath("$.tax").value(18000.00))
                .andExpect(jsonPath("$.total").value(118000.00));

        // 5) Proveedor liquida la reserva:
        //    Retención = min(118,000.00, 200,000.00) = 118,000.00
        //    Devolución al cliente = 82,000.00
        //    Saldo SETTLEMENT = 0.00 -> emite ReservationSettled
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/settle", reservationId)
                        .with(JwtRequestPostProcessors.asProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refundMethod": "SIMULATED",
                                  "refundReference": "REF-SETTLEMENT-FLOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementInvoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.settlementInvoice.retainedDeposit").value(118000.00))
                .andExpect(jsonPath("$.settlementInvoice.balance").value(0.00))
                .andExpect(jsonPath("$.settlementInvoice.paid").value(true))
                .andExpect(jsonPath("$.securityDeposit.settled").value(true))
                .andExpect(jsonPath("$.securityDeposit.totalRetained").value(118000.00))
                .andExpect(jsonPath("$.securityDeposit.totalRefunded").value(82000.00));

        assertThat(capturedEvents.settledEvents()).hasSize(1);
        ReservationSettled settledEvent = capturedEvents.settledEvents().getFirst();
        assertThat(settledEvent.reservationId()).isEqualTo(reservationId);
        assertThat(settledEvent.depositRefunded()).isEqualByComparingTo("82000.00");
        assertThat(settledEvent.extraCharges()).isEqualByComparingTo("118000.00");

        // 6) Consultar reporte financiero como ADMIN
        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiced").exists())
                .andExpect(jsonPath("$.totalCollected").exists())
                .andExpect(jsonPath("$.depositsRetained").exists())
                .andExpect(jsonPath("$.depositsRefunded").exists())
                .andExpect(jsonPath("$.breakdownByPaymentMethod.SIMULATED").exists());
    }

    private void seedDatabaseFixture(
            UUID clientId,
            UUID providerId,
            UUID toolId,
            UUID unitId,
            UUID reservationId,
            UUID reservationUnitId,
            Instant now
    ) {
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)",
                toBytes(clientId), "client-flow@rentatools.com", "hash", "Cliente Flow", ts, ts
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)",
                toBytes(providerId), "provider-flow@rentatools.com", "hash", "Proveedor Flow", ts, ts
        );
        jdbcTemplate.update(
                "INSERT INTO tools (id, provider_id, name, deposit_amount, status, created_at, updated_at, version) VALUES (?, ?, ?, 200000.00, 'ACTIVE', ?, ?, 0)",
                toBytes(toolId), toBytes(providerId), "Rotomartillo Industrial", ts, ts
        );
        jdbcTemplate.update(
                "INSERT INTO tool_units (id, tool_id, serial_number, status, created_at) VALUES (?, ?, ?, 'AVAILABLE', ?)",
                toBytes(unitId), toBytes(toolId), "SN-FLOW-01", ts
        );
        jdbcTemplate.update(
                "INSERT INTO reservations (id, code, client_id, provider_id, start_date, end_date, status, created_at, updated_at, version) VALUES (?, 'RES-FLOW-01', ?, ?, ?, ?, 'CONFIRMED', ?, ?, 0)",
                toBytes(reservationId),
                toBytes(clientId),
                toBytes(providerId),
                Date.valueOf(LocalDate.of(2026, 6, 10)),
                Date.valueOf(LocalDate.of(2026, 6, 12)),
                ts,
                ts
        );
        jdbcTemplate.update(
                "INSERT INTO reservation_units (id, reservation_id, unit_id, daily_rate, agreed_amount, deposit_amount) VALUES (?, ?, ?, 20000.00, 100000.00, 200000.00)",
                toBytes(reservationUnitId), toBytes(reservationId), toBytes(unitId)
        );
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    @TestConfiguration
    static class BillingEventsCaptureConfig {

        @Bean
        public CapturedBillingEvents capturedBillingEvents() {
            return new CapturedBillingEvents();
        }
    }

    static class CapturedBillingEvents {

        private final List<ReservationClearedForDelivery> clearedEvents = new ArrayList<>();
        private final List<ReservationSettled> settledEvents = new ArrayList<>();

        @EventListener
        public void onCleared(ReservationClearedForDelivery event) {
            clearedEvents.add(event);
        }

        @EventListener
        public void onSettled(ReservationSettled event) {
            settledEvents.add(event);
        }

        public List<ReservationClearedForDelivery> clearedEvents() {
            return List.copyOf(clearedEvents);
        }

        public List<ReservationSettled> settledEvents() {
            return List.copyOf(settledEvents);
        }

        public void clear() {
            clearedEvents.clear();
            settledEvents.clear();
        }
    }
}
