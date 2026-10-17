package com.rentatools.rental.infrastructure;

import com.rentatools.rental.application.dto.ChecklistItemView;
import com.rentatools.rental.application.dto.ChecklistResultView;
import com.rentatools.rental.application.dto.FrequentClientReportView;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.application.dto.ToolStatusReportView;
import com.rentatools.rental.application.dto.TopToolReportView;
import com.rentatools.rental.application.port.in.AttachInspectionPhotoUseCase;
import com.rentatools.rental.application.port.in.GetInspectionQuery;
import com.rentatools.rental.application.port.in.GetRentalReportsQuery;
import com.rentatools.rental.application.port.in.ListChecklistItemsQuery;
import com.rentatools.rental.application.port.in.ManageChecklistUseCase;
import com.rentatools.rental.application.port.in.RegisterInspectionUseCase;
import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.inspection.ResultValue;
import com.rentatools.rental.infrastructure.adapter.in.web.AdminChecklistController;
import com.rentatools.rental.infrastructure.adapter.in.web.AdminRentalReportController;
import com.rentatools.rental.infrastructure.adapter.in.web.InspectionController;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import com.rentatools.support.JwtRequestPostProcessors;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        InspectionController.class,
        AdminChecklistController.class,
        AdminRentalReportController.class
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
class RentalInspectionWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterInspectionUseCase registerInspectionUseCase;

    @MockitoBean
    private AttachInspectionPhotoUseCase attachInspectionPhotoUseCase;

    @MockitoBean
    private GetInspectionQuery getInspectionQuery;

    @MockitoBean
    private ManageChecklistUseCase manageChecklistUseCase;

    @MockitoBean
    private ListChecklistItemsQuery listChecklistItemsQuery;

    @MockitoBean
    private GetRentalReportsQuery getRentalReportsQuery;

    @Test
    @DisplayName("201 Created al registrar inspección con rol PROVEEDOR")
    void returns201OnRegisterInspection() throws Exception {
        UUID resId = Ids.newId();
        UUID unitId = Ids.newId();
        UUID inspectionId = Ids.newId();

        InspectionView view = new InspectionView(
                inspectionId,
                unitId,
                InspectionType.DELIVERY,
                Ids.newId(),
                Instant.now(),
                OverallCondition.GOOD,
                "Todo en orden",
                List.of(new ChecklistResultView(1, "CARCASA", "Carcasa", ResultValue.OK, null)),
                List.of()
        );
        when(registerInspectionUseCase.registerInspection(any(), any())).thenReturn(view);

        mockMvc.perform(post("/api/v1/reservations/{resId}/units/{unitId}/inspections", resId, unitId)
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "DELIVERY",
                                  "overallCondition": "GOOD",
                                  "observations": "Todo en orden",
                                  "results": [
                                    {"checklistItemId": 1, "result": "OK", "note": "Perfecto"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(inspectionId.toString()))
                .andExpect(jsonPath("$.type").value("DELIVERY"))
                .andExpect(jsonPath("$.overallCondition").value("GOOD"));
    }

    @Test
    @DisplayName("200 OK al consultar inspección por ID")
    void returns200OnGetInspection() throws Exception {
        UUID inspectionId = Ids.newId();
        InspectionView view = new InspectionView(
                inspectionId,
                Ids.newId(),
                InspectionType.DELIVERY,
                Ids.newId(),
                Instant.now(),
                OverallCondition.GOOD,
                null,
                List.of(),
                List.of()
        );
        when(getInspectionQuery.getInspection(any(), eq(inspectionId))).thenReturn(view);

        mockMvc.perform(get("/api/v1/inspections/{id}", inspectionId)
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inspectionId.toString()));
    }

    @Test
    @DisplayName("201 Created al subir fotografía de evidencia")
    void returns201OnAttachPhoto() throws Exception {
        UUID inspectionId = Ids.newId();
        UUID photoId = Ids.newId();

        InspectionPhotoView photoView = new InspectionPhotoView(
                photoId,
                inspectionId,
                "inspections/" + inspectionId + "/foto.jpg",
                "image/jpeg",
                1024L,
                Instant.now()
        );
        when(attachInspectionPhotoUseCase.attachPhoto(any(), any())).thenReturn(photoView);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "foto.jpg",
                "image/jpeg",
                "datos-foto".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/inspections/{id}/photos", inspectionId)
                        .file(file)
                        .with(JwtRequestPostProcessors.asProvider()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(photoId.toString()))
                .andExpect(jsonPath("$.storageKey").value("inspections/" + inspectionId + "/foto.jpg"));
    }

    @Test
    @DisplayName("200 OK al descargar foto de inspección")
    void returns200OnGetPhoto() throws Exception {
        UUID inspectionId = Ids.newId();
        UUID photoId = Ids.newId();

        InspectionPhotoView photoView = new InspectionPhotoView(
                photoId,
                inspectionId,
                "inspections/key.jpg",
                "image/jpeg",
                4L,
                Instant.now()
        );
        GetInspectionQuery.InspectionPhotoResource resource = new GetInspectionQuery.InspectionPhotoResource(
                photoView,
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4})
        );
        when(getInspectionQuery.getInspectionPhotoResource(any(), eq(inspectionId), eq(photoId))).thenReturn(resource);

        mockMvc.perform(get("/api/v1/inspections/{id}/photos/{photoId}", inspectionId, photoId)
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    @DisplayName("200/201 en gestión de ítems de checklist por ADMIN")
    void returns200And201OnAdminChecklist() throws Exception {
        ChecklistItemView itemView = new ChecklistItemView(10, "BATERIA", "Batería cargada", true);
        when(manageChecklistUseCase.createItem(any(), any())).thenReturn(itemView);
        when(manageChecklistUseCase.updateItem(any(), eq(10), any())).thenReturn(itemView);
        when(listChecklistItemsQuery.listItems(anyBoolean())).thenReturn(List.of(itemView));

        mockMvc.perform(post("/api/v1/admin/checklist-items")
                        .with(JwtRequestPostProcessors.asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "BATERIA",
                                  "label": "Batería cargada"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BATERIA"));

        mockMvc.perform(get("/api/v1/admin/checklist-items")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BATERIA"));

        mockMvc.perform(put("/api/v1/admin/checklist-items/{id}", 10)
                        .with(JwtRequestPostProcessors.asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Batería cargada y probada",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("200 OK en reportes de administración (top-tools, frequent-clients, tool-status)")
    void returns200OnAdminReports() throws Exception {
        when(getRentalReportsQuery.getTopTools(any(), anyInt())).thenReturn(List.of(
                new TopToolReportView(Ids.newId(), "Amoladora", "DeWalt", "DWE4020", 5L, 15L)
        ));
        when(getRentalReportsQuery.getFrequentClients(any(), anyInt())).thenReturn(List.of(
                new FrequentClientReportView(Ids.newId(), "Carlos Perez", "carlos@test.com", 6L, 3L, 3L, 0L)
        ));
        when(getRentalReportsQuery.getToolStatusSummary(any())).thenReturn(
                new ToolStatusReportView(50L, 35L, 5L, 10L, 8L)
        );

        mockMvc.perform(get("/api/v1/admin/reports/top-tools")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Amoladora"))
                .andExpect(jsonPath("$[0].totalRentals").value(5));

        mockMvc.perform(get("/api/v1/admin/reports/frequent-clients")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Carlos Perez"))
                .andExpect(jsonPath("$[0].closedReservations").value(3));

        mockMvc.perform(get("/api/v1/admin/reports/tool-status")
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(50))
                .andExpect(jsonPath("$.availableUnits").value(35))
                .andExpect(jsonPath("$.activeOccupiedUnits").value(8));
    }

    @Test
    @DisplayName("401 Unauthorized sin credenciales y 403 Forbidden para rol no autorizado")
    void handlesAuthErrorsProperly() throws Exception {
        // 401 sin token
        mockMvc.perform(get("/api/v1/admin/reports/top-tools"))
                .andExpect(status().isUnauthorized());

        // 403 CLIENTE intentando acceder a reportes de ADMIN
        mockMvc.perform(get("/api/v1/admin/reports/top-tools")
                        .with(JwtRequestPostProcessors.asClient()))
                .andExpect(status().isForbidden());

        // 403 CLIENTE intentando administrar checklist items
        mockMvc.perform(post("/api/v1/admin/checklist-items")
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"label\":\"Y\"}"))
                .andExpect(status().isForbidden());

        // 403 CLIENTE intentando registrar inspección (solo PROVEEDOR o ADMIN)
        mockMvc.perform(post("/api/v1/reservations/{resId}/units/{unitId}/inspections", Ids.newId(), Ids.newId())
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELIVERY\",\"overallCondition\":\"GOOD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("400 Bad Request en payloads inválidos o incompletos")
    void returns400OnInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/{resId}/units/{unitId}/inspections", Ids.newId(), Ids.newId())
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
