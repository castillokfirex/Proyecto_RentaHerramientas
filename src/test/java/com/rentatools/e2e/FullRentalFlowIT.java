package com.rentatools.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import com.rentatools.shared.integration.rental.ReservationReturned;
import com.rentatools.support.AbstractMySqlIT;
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
 * Prueba de integración de extremo a extremo (E2E) que recorre el ciclo completo:
 * 1. Login y autenticación real JWT para CLIENTE, PROVEEDOR y ADMIN.
 * 2. Consulta de perfiles en /api/v1/users/me y listado administrativo en /api/v1/admin/users.
 * 3. Simulación del ciclo de vida de alquiler y facturación:
 *    - Confirmación de reserva (ReservationConfirmed).
 *    - Pago de factura RENTAL y cobro de garantía (SecurityDeposit).
 *    - Emisión del evento ReservationClearedForDelivery.
 *    - Devolución con mora (ReservationReturned).
 *    - Agregado de cargo por daño por parte del PROVEEDOR.
 *    - Liquidación final de garantía y emisión de ReservationSettled.
 * 4. Consulta del reporte financiero global de ADMIN (/api/v1/admin/reports/revenue).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FullRentalFlowIT.EventsCaptureConfig.class)
class FullRentalFlowIT extends AbstractMySqlIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturedEvents capturedEvents;

    @BeforeEach
    void resetCapturedEvents() {
        capturedEvents.clear();
    }

    @Test
    @DisplayName("Flujo E2E completo: Login real JWT -> Perfil -> Reserva -> Pago -> Garantía -> Mora -> Daño -> Liquidación -> Reporte")
    void executesCompleteE2EFlowSuccessfully() throws Exception {
        // ------------------------------------------------------------
        // 1. AUTENTICACIÓN: Login real vía POST /api/v1/auth/login
        // ------------------------------------------------------------
        String adminToken = loginAndGetToken("admin@rentatools.com", "Admin1234!");
        String providerToken = loginAndGetToken("proveedor1.dev@rentatools.com", "Password123!");
        String clientToken = loginAndGetToken("cliente1@rentatools.com", "Password123!");

        assertThat(adminToken).isNotBlank();
        assertThat(providerToken).isNotBlank();
        assertThat(clientToken).isNotBlank();

        // ------------------------------------------------------------
        // 2. CONSULTA DE PERFILES: GET /api/v1/users/me y GET /api/v1/admin/users
        // ------------------------------------------------------------
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente1@rentatools.com"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("proveedor1.dev@rentatools.com"))
                .andExpect(jsonPath("$.roles[0]").value("PROVEEDOR"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)));

        // ------------------------------------------------------------
        // 3. FIXTURE DE RESERVA (Conectando con datos de usuarios y herramientas existentes)
        // ------------------------------------------------------------
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID clientId = UUID.fromString("01900000-0000-7000-8000-000000000009");      // cliente1@rentatools.com
        UUID providerId = UUID.fromString("01900000-0000-7000-8000-000000000002");    // proveedor1.dev@rentatools.com
        UUID toolUnitId = UUID.fromString("01900000-0000-7000-8300-000000000001");    // SN-BOSCH-001
        UUID reservationId = Ids.newId();
        UUID reservationUnitId = Ids.newId();

        seedReservation(reservationId, reservationUnitId, clientId, providerId, toolUnitId, "RES-E2E-99", now);

        // ------------------------------------------------------------
        // 4. CONFIRMACIÓN DE RESERVA: Evento ReservationConfirmed
        // ------------------------------------------------------------
        eventPublisher.publishEvent(new ReservationConfirmed(
                Ids.newId(),
                now,
                reservationId,
                "RES-E2E-99",
                clientId,
                providerId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 5),
                providerId,
                List.of(new ReservationConfirmed.Line(
                        reservationUnitId,
                        "Rotomartillo SDS-Plus 800W",
                        "SN-BOSCH-001",
                        new BigDecimal("100000.00"), // Subtotal RENTAL
                        new BigDecimal("200000.00")  // Garantía requerida
                ))
        ));

        // Consultar estado de facturación como CLIENTE
        MvcResult resBilling = mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalInvoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.rentalInvoice.total").value(118000.00)) // 100,000 + 18% IVA
                .andExpect(jsonPath("$.securityDeposit.requiredAmount").value(200000.00))
                .andReturn();

        JsonNode billingTree = objectMapper.readTree(resBilling.getResponse().getContentAsString());
        UUID rentalInvoiceId = UUID.fromString(billingTree.get("rentalInvoice").get("id").asText());

        // ------------------------------------------------------------
        // 5. PAGO DE RENTAL Y COBRO DE GARANTÍA
        // ------------------------------------------------------------
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/payments", rentalInvoiceId)
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 118000.00,
                                  "method": "SIMULATED",
                                  "reference": "E2E-RENTAL-PAY-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.balance").value(0.00));

        mockMvc.perform(post("/api/v1/billing/reservations/{id}/deposit/collections", reservationId)
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 200000.00,
                                  "method": "SIMULATED",
                                  "reference": "E2E-DEP-COL-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyCollected").value(true));

        // Debe haberse emitido el evento de autorización de entrega
        assertThat(capturedEvents.clearedEvents()).hasSize(1);
        assertThat(capturedEvents.clearedEvents().getFirst().reservationId()).isEqualTo(reservationId);

        // ------------------------------------------------------------
        // 6. DEVOLUCIÓN CON MORA: Evento ReservationReturned
        // ------------------------------------------------------------
        // 2 días de mora * (25,000 * 1.5) = 75,000.00 de subtotal mora
        eventPublisher.publishEvent(new ReservationReturned(
                Ids.newId(),
                now.plusSeconds(120),
                reservationId,
                providerId,
                List.of(new ReservationReturned.ReturnedUnit(
                        reservationUnitId,
                        "Rotomartillo SDS-Plus 800W",
                        LocalDate.of(2026, 7, 7),
                        2,
                        new BigDecimal("25000.00"),
                        true,
                        false
                ))
        ));

        // PROVEEDOR consulta el borrador de liquidación (SETTLEMENT)
        MvcResult draftResult = mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementInvoice.status").value("DRAFT"))
                .andExpect(jsonPath("$.settlementInvoice.subtotal").value(75000.00))
                .andReturn();

        UUID settlementInvoiceId = UUID.fromString(
                objectMapper.readTree(draftResult.getResponse().getContentAsString())
                        .get("settlementInvoice").get("id").asText()
        );

        // ------------------------------------------------------------
        // 7. PROVEEDOR AÑADE CARGO POR DAÑO Y LIQUIDA LA GARANTÍA
        // ------------------------------------------------------------
        // Daño de 25,000 -> Subtotal = 75,000 + 25,000 = 100,000.00 -> IVA 18,000.00 -> Total SETTLEMENT = 118,000.00
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/damage-charges", settlementInvoiceId)
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationUnitId": "%s",
                                  "description": "Reparación de mandril y cable",
                                  "amount": 25000.00
                                }
                                """.formatted(reservationUnitId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(100000.00))
                .andExpect(jsonPath("$.tax").value(18000.00))
                .andExpect(jsonPath("$.total").value(118000.00));

        // Liquidación: retención de 118,000 de la garantía y reembolso de 82,000 al cliente
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/settle", reservationId)
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refundMethod": "SIMULATED",
                                  "refundReference": "E2E-REFUND-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementInvoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.settlementInvoice.paid").value(true))
                .andExpect(jsonPath("$.securityDeposit.settled").value(true))
                .andExpect(jsonPath("$.securityDeposit.totalRetained").value(118000.00))
                .andExpect(jsonPath("$.securityDeposit.totalRefunded").value(82000.00));

        // Debe haberse emitido ReservationSettled
        assertThat(capturedEvents.settledEvents()).hasSize(1);
        ReservationSettled settled = capturedEvents.settledEvents().getFirst();
        assertThat(settled.reservationId()).isEqualTo(reservationId);
        assertThat(settled.extraCharges()).isEqualByComparingTo("118000.00");
        assertThat(settled.depositRefunded()).isEqualByComparingTo("82000.00");

        // ------------------------------------------------------------
        // 8. REPORTE FINANCIERO DE ADMINISTRACIÓN
        // ------------------------------------------------------------
        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiced").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.totalCollected").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.breakdownByPaymentMethod.SIMULATED").exists());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private void seedReservation(
            UUID reservationId,
            UUID reservationUnitId,
            UUID clientId,
            UUID providerId,
            UUID unitId,
            String code,
            Instant now
    ) {
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO reservations (id, code, client_id, provider_id, start_date, end_date, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?, 0)
                """,
                uuidToBytes(reservationId),
                code,
                uuidToBytes(clientId),
                uuidToBytes(providerId),
                Date.valueOf(LocalDate.of(2026, 7, 1)),
                Date.valueOf(LocalDate.of(2026, 7, 5)),
                ts,
                ts
        );
        jdbcTemplate.update(
                """
                INSERT INTO reservation_units (id, reservation_id, unit_id, daily_rate, agreed_amount, deposit_amount)
                VALUES (?, ?, ?, 25000.00, 100000.00, 200000.00)
                """,
                uuidToBytes(reservationUnitId),
                uuidToBytes(reservationId),
                uuidToBytes(unitId)
        );
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buf = ByteBuffer.wrap(new byte[16]);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    @TestConfiguration
    static class EventsCaptureConfig {
        @Bean
        public CapturedEvents capturedEvents() {
            return new CapturedEvents();
        }
    }

    static class CapturedEvents {
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
