package com.rentatools.billing.infrastructure;

import com.rentatools.billing.application.dto.InvoiceLineView;
import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.application.dto.PaymentView;
import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.application.dto.RevenueReportView;
import com.rentatools.billing.application.dto.SecurityDepositView;
import com.rentatools.billing.application.port.in.AddDamageChargeUseCase;
import com.rentatools.billing.application.port.in.CollectDepositUseCase;
import com.rentatools.billing.application.port.in.GetInvoiceQuery;
import com.rentatools.billing.application.port.in.GetReservationBillingQuery;
import com.rentatools.billing.application.port.in.GetRevenueReportQuery;
import com.rentatools.billing.application.port.in.RegisterPaymentUseCase;
import com.rentatools.billing.application.port.in.SettleReservationUseCase;
import com.rentatools.billing.domain.model.invoice.InvoiceStatus;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.domain.model.invoice.LineConcept;
import com.rentatools.billing.domain.model.invoice.PaymentKind;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.billing.infrastructure.adapter.in.web.AdminBillingReportController;
import com.rentatools.billing.infrastructure.adapter.in.web.InvoiceController;
import com.rentatools.billing.infrastructure.adapter.in.web.ReservationBillingController;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import com.rentatools.support.DevUsers;
import com.rentatools.support.JwtRequestPostProcessors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code @WebMvcTest} para {@link InvoiceController}, {@link ReservationBillingController}
 * y {@link AdminBillingReportController}, verificando validación de DTOs, permisos por rol
 * ({@code @PreAuthorize}) y códigos HTTP 200, 400, 401 y 403.
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
        ClockConfig.class
})
@ActiveProfiles("test")
class BillingControllersWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetInvoiceQuery getInvoiceQuery;

    @MockitoBean
    private RegisterPaymentUseCase registerPaymentUseCase;

    @MockitoBean
    private AddDamageChargeUseCase addDamageChargeUseCase;

    @MockitoBean
    private GetReservationBillingQuery getReservationBillingQuery;

    @MockitoBean
    private CollectDepositUseCase collectDepositUseCase;

    @MockitoBean
    private SettleReservationUseCase settleReservationUseCase;

    @MockitoBean
    private GetRevenueReportQuery getRevenueReportQuery;

    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    @DisplayName("InvoiceController: verifica 200, 400 (validación), 401 (sin JWT) y 403 (CLIENTE en damage-charges)")
    void invoiceControllerHandles200And400And401And403() throws Exception {
        UUID invoiceId = Ids.newId();
        UUID reservationId = Ids.newId();
        UUID unitId = Ids.newId();

        InvoiceView sampleView = new InvoiceView(
                invoiceId,
                reservationId,
                InvoiceType.RENTAL,
                InvoiceStatus.ISSUED,
                10L,
                new BigDecimal("0.1900"),
                NOW,
                null,
                NOW,
                List.of(new InvoiceLineView(
                        Ids.newId(),
                        unitId,
                        LineConcept.RENTAL,
                        "Alquiler taladro",
                        new BigDecimal("1.00"),
                        new BigDecimal("100000.00"),
                        new BigDecimal("100000.00")
                )),
                List.of(new PaymentView(
                        Ids.newId(),
                        PaymentKind.PAYMENT,
                        new BigDecimal("119000.00"),
                        PaymentMethod.SIMULATED,
                        "SIM-01",
                        NOW,
                        DevUsers.CLIENT_ID
                )),
                new BigDecimal("100000.00"),
                new BigDecimal("19000.00"),
                new BigDecimal("119000.00"),
                new BigDecimal("119000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                true
        );

        given(getInvoiceQuery.getInvoice(any(), eq(invoiceId))).willReturn(sampleView);
        given(registerPaymentUseCase.registerPayment(any())).willReturn(sampleView);
        given(addDamageChargeUseCase.addDamageCharge(any())).willReturn(sampleView);

        // 401 sin token
        mockMvc.perform(get("/api/v1/billing/invoices/{id}", invoiceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 200 OK consultando factura con JWT de CLIENTE
        mockMvc.perform(get("/api/v1/billing/invoices/{id}", invoiceId)
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceId.toString()))
                .andExpect(jsonPath("$.total").value(119000.00))
                .andExpect(jsonPath("$.paid").value(true));

        // 400 Bad Request en pago con monto negativo / método nulo
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/payments", invoiceId)
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 0,
                                  "method": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 200 OK registrando pago válido
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/payments", invoiceId)
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 119000.00,
                                  "method": "SIMULATED",
                                  "reference": "SIM-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true));

        // 403 Forbidden cuando CLIENTE intenta agregar cargo por daño
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/damage-charges", invoiceId)
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationUnitId": "%s",
                                  "description": "Daño en carcasa",
                                  "amount": 50000.00
                                }
                                """.formatted(unitId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 200 OK cuando PROVEEDOR agrega cargo por daño
        mockMvc.perform(post("/api/v1/billing/invoices/{id}/damage-charges", invoiceId)
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationUnitId": "%s",
                                  "description": "Daño en carcasa",
                                  "amount": 50000.00
                                }
                                """.formatted(unitId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ReservationBillingController: verifica 200, 400, 401 y 403 en cobro de garantía y liquidación")
    void reservationBillingControllerHandles200And400And401And403() throws Exception {
        UUID reservationId = Ids.newId();
        SecurityDepositView depositView = new SecurityDepositView(
                Ids.newId(),
                reservationId,
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("100000.00"),
                true,
                false,
                NOW,
                null,
                List.of(),
                List.of()
        );
        ReservationBillingView billingView = new ReservationBillingView(reservationId, null, null, depositView);

        given(getReservationBillingQuery.getReservationBilling(any(), eq(reservationId))).willReturn(billingView);
        given(collectDepositUseCase.collectDeposit(any())).willReturn(depositView);
        given(settleReservationUseCase.settleReservation(any())).willReturn(billingView);

        // 401 sin token
        mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId))
                .andExpect(status().isUnauthorized());

        // 200 OK consultando facturación de reserva
        mockMvc.perform(get("/api/v1/billing/reservations/{id}", reservationId)
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.securityDeposit.fullyCollected").value(true));

        // 400 Bad Request en cobro de garantía sin monto
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/deposit/collections", reservationId)
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "method": "SIMULATED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 200 OK cobrando garantía
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/deposit/collections", reservationId)
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100000.00,
                                  "method": "SIMULATED",
                                  "reference": "DEP-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyCollected").value(true));

        // 403 Forbidden cuando CLIENTE intenta liquidar (/settle)
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/settle", reservationId)
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 200 OK cuando PROVEEDOR liquida la reserva
        mockMvc.perform(post("/api/v1/billing/reservations/{id}/settle", reservationId)
                        .with(JwtRequestPostProcessors.asProvider()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()));
    }

    @Test
    @DisplayName("AdminBillingReportController: exige rol ADMIN (401/403) y devuelve métricas financieras en 200 OK")
    void adminBillingReportControllerEnforcesAdminRoleAndReturnsReport() throws Exception {
        RevenueReportView reportView = new RevenueReportView(
                new BigDecimal("500000.00"),
                new BigDecimal("420000.00"),
                new BigDecimal("80000.00"),
                new BigDecimal("120000.00"),
                Map.of(
                        PaymentMethod.SIMULATED, new BigDecimal("300000.00"),
                        PaymentMethod.CARD, new BigDecimal("120000.00"),
                        PaymentMethod.CASH, new BigDecimal("0.00"),
                        PaymentMethod.TRANSFER, new BigDecimal("0.00")
                )
        );
        given(getRevenueReportQuery.getRevenueReport(any(), any(), any())).willReturn(reportView);

        // 401 sin token
        mockMvc.perform(get("/api/v1/admin/reports/revenue"))
                .andExpect(status().isUnauthorized());

        // 403 para CLIENTE y PROVEEDOR
        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .with(JwtRequestPostProcessors.asProvider()))
                .andExpect(status().isForbidden());

        // 200 OK para ADMIN
        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoiced").value(500000.00))
                .andExpect(jsonPath("$.totalCollected").value(420000.00))
                .andExpect(jsonPath("$.depositsRetained").value(80000.00))
                .andExpect(jsonPath("$.depositsRefunded").value(120000.00))
                .andExpect(jsonPath("$.breakdownByPaymentMethod.SIMULATED").value(300000.00));
    }
}
