package com.rentatools.billing.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentatools.billing.application.BillingTestFakes.InMemoryInvoiceNumberAllocator;
import com.rentatools.billing.application.BillingTestFakes.InMemoryInvoiceRepository;
import com.rentatools.billing.application.BillingTestFakes.InMemoryReservationParticipantsPort;
import com.rentatools.billing.application.BillingTestFakes.InMemorySecurityDepositRepository;
import com.rentatools.billing.application.port.out.InvoiceNumberAllocator;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.application.port.out.SecurityDepositRepository;
import com.rentatools.billing.application.service.BillingQueryService;
import com.rentatools.billing.application.service.DepositService;
import com.rentatools.billing.application.service.PaymentService;
import com.rentatools.billing.application.service.RentalBillingService;
import com.rentatools.billing.application.service.SettlementService;
import com.rentatools.billing.domain.service.InvoiceBalanceCalculator;
import com.rentatools.billing.domain.service.LateFeePolicy;
import com.rentatools.billing.domain.service.SettlementCalculator;
import com.rentatools.billing.infrastructure.adapter.in.event.RentalEventsListener;
import com.rentatools.billing.infrastructure.adapter.in.web.AdminBillingReportController;
import com.rentatools.billing.infrastructure.adapter.in.web.InvoiceController;
import com.rentatools.billing.infrastructure.adapter.in.web.ReservationBillingController;
import com.rentatools.billing.infrastructure.config.BillingProperties;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.event.SpringIntegrationEventPublisher;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import com.rentatools.shared.integration.rental.ReservationReturned;
import com.rentatools.support.JwtRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración del ciclo financiero completo en el contexto de Spring:
 * publica eventos sintéticos con {@link ApplicationEventPublisher} y verifica la reacción síncrona de
 * {@link RentalEventsListener}, las llamadas REST con {@link MockMvc} y la emisión de
 * {@link ReservationClearedForDelivery} y {@link ReservationSettled}.
 */
@WebMvcTest(controllers = {
        InvoiceController.class,
        ReservationBillingController.class,
        AdminBillingReportController.class
})
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        RestAuthErrorHandlers.class,
        CurrentActorArgumentResolver.class,
        WebMvcConfig.class,
        GlobalExceptionHandler.class,
        ClockConfig.class,
        BillingProperties.class,
        SpringIntegrationEventPublisher.class,
        LateFeePolicy.class,
        InvoiceBalanceCalculator.class,
        SettlementCalculator.class,
        RentalBillingService.class,
        PaymentService.class,
        DepositService.class,
        SettlementService.class,
        BillingQueryService.class,
        RentalEventsListener.class,
        BillingFlowTest.InMemoryBillingTestConfig.class
})
@ActiveProfiles("test")
class BillingFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private InMemoryReservationParticipantsPort participantsPort;

    @Autowired
    private CapturedEvents capturedEvents;

    @BeforeEach
    void resetCapturedEvents() {
        capturedEvents.clear();
    }

    @Test
    @DisplayName("Recorre confirmación -> pago + garantía -> devolución con mora -> daño -> liquidación con retención y reporte ADMIN")
    void fullBillingCycleFromRentalEventsThroughRestControllersToSettlementAndAdminReport() throws Exception {
        UUID reservationId = Ids.newId();
        UUID reservationUnitId = Ids.newId();
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();
        Instant now = Instant.parse("2026-06-10T10:00:00Z");

        participantsPort.register(reservationId, "RES-2026-01", clientId, providerId);

        // 1) Publicar ReservationConfirmed:
        //    agreedAmount = 100,000.00 -> con app.tax-rate = 0.18 => IVA = 18,000.00, Total RENTAL = 118,000.00
        //    depositAmount = 200,000.00
        applicationEventPublisher.publishEvent(new ReservationConfirmed(
                Ids.newId(),
                now,
                reservationId,
                "RES-2026-01",
                clientId,
                providerId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 12),
                providerId,
                List.of(new ReservationConfirmed.Line(
                        reservationUnitId,
                        "Taladro Percutor",
                        "SN-TP-01",
                        new BigDecimal("100000.00"),
                        new BigDecimal("200000.00")
                ))
        ));

        MvcResult initialBillingResult = mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId)
                        .with(JwtRequestPostProcessors.asClient(clientId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalInvoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.rentalInvoice.total").value(118000.00))
                .andExpect(jsonPath("$.securityDeposit.requiredAmount").value(200000.00))
                .andReturn();

        JsonNode initialJson = objectMapper.readTree(initialBillingResult.getResponse().getContentAsString());
        UUID rentalInvoiceId = UUID.fromString(initialJson.get("rentalInvoice").get("id").asText());

        // 2) Pagar RENTAL + cobrar garantía -> se publica ReservationClearedForDelivery
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/payments", rentalInvoiceId)
                        .with(JwtRequestPostProcessors.asClient(clientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 118000.00,
                                  "method": "SIMULATED",
                                  "reference": "PAY-RENTAL-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true));

        assertThat(capturedEvents.cleared()).isEmpty();

        mockMvc.perform(post("/api/v1/billing/reservations/{id}/deposit/collections", reservationId)
                        .with(JwtRequestPostProcessors.asClient(clientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 200000.00,
                                  "method": "SIMULATED",
                                  "reference": "DEP-COLLECT-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyCollected").value(true));

        assertThat(capturedEvents.cleared()).hasSize(1);
        assertThat(capturedEvents.cleared().getFirst().reservationId()).isEqualTo(reservationId);

        // 3) Publicar ReservationReturned con 2 días de mora y dailyRate = 20,000.00
        //    Mora = 2 * (20,000 * 1.5) = 60,000.00
        applicationEventPublisher.publishEvent(new ReservationReturned(
                Ids.newId(),
                now.plusSeconds(3600),
                reservationId,
                providerId,
                List.of(new ReservationReturned.ReturnedUnit(
                        reservationUnitId,
                        "Taladro Percutor",
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

        // 4) Proveedor agrega cargo por daño de 40,000.00 -> Subtotal = 100,000.00, IVA 18% = 18,000.00, Total = 118,000.00
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/damage-charges", settlementInvoiceId)
                        .with(JwtRequestPostProcessors.asProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationUnitId": "%s",
                                  "description": "Reparación de carcasa",
                                  "amount": 40000.00
                                }
                                """.formatted(reservationUnitId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(118000.00));

        // 5) Proveedor liquida: retención = 118,000.00, devolución = 82,000.00 -> emite ReservationSettled
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/settle", reservationId)
                        .with(JwtRequestPostProcessors.asProvider(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementInvoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.settlementInvoice.retainedDeposit").value(118000.00))
                .andExpect(jsonPath("$.settlementInvoice.balance").value(0.00))
                .andExpect(jsonPath("$.securityDeposit.settled").value(true))
                .andExpect(jsonPath("$.securityDeposit.totalRefunded").value(82000.00));

        assertThat(capturedEvents.settled()).hasSize(1);
        ReservationSettled settledEvent = capturedEvents.settled().getFirst();
        assertThat(settledEvent.reservationId()).isEqualTo(reservationId);
        assertThat(settledEvent.depositRefunded()).isEqualByComparingTo("82000.00");
        assertThat(settledEvent.extraCharges()).isEqualByComparingTo("118000.00");

        // 6) Consultar reporte financiero como ADMIN
        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiced").value(236000.00))
                .andExpect(jsonPath("$.totalCollected").value(118000.00))
                .andExpect(jsonPath("$.depositsRetained").value(118000.00))
                .andExpect(jsonPath("$.depositsRefunded").value(82000.00))
                .andExpect(jsonPath("$.breakdownByPaymentMethod.SIMULATED").value(118000.00));
    }

    @TestConfiguration
    static class InMemoryBillingTestConfig {

        @Bean
        InvoiceRepository invoiceRepository() {
            return new InMemoryInvoiceRepository();
        }

        @Bean
        SecurityDepositRepository securityDepositRepository() {
            return new InMemorySecurityDepositRepository();
        }

        @Bean
        InvoiceNumberAllocator invoiceNumberAllocator() {
            return new InMemoryInvoiceNumberAllocator();
        }

        @Bean
        InMemoryReservationParticipantsPort reservationParticipantsPort() {
            return new InMemoryReservationParticipantsPort();
        }

        @Bean
        CapturedEvents capturedEvents() {
            return new CapturedEvents();
        }
    }

    static class CapturedEvents {

        private final List<ReservationClearedForDelivery> cleared = new ArrayList<>();
        private final List<ReservationSettled> settled = new ArrayList<>();

        @EventListener
        public void onCleared(ReservationClearedForDelivery event) {
            cleared.add(event);
        }

        @EventListener
        public void onSettled(ReservationSettled event) {
            settled.add(event);
        }

        public List<ReservationClearedForDelivery> cleared() {
            return List.copyOf(cleared);
        }

        public List<ReservationSettled> settled() {
            return List.copyOf(settled);
        }

        public void clear() {
            cleared.clear();
            settled.clear();
        }
    }
}
