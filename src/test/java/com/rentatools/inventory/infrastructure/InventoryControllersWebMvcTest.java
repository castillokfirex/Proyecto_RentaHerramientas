package com.rentatools.inventory.infrastructure;

import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.inventory.application.dto.CategoryView;
import com.rentatools.inventory.application.dto.ToolImageView;
import com.rentatools.inventory.application.dto.ToolUnitView;
import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.inventory.application.port.in.ChangeUnitStatusUseCase;
import com.rentatools.inventory.application.port.in.CreateCategoryUseCase;
import com.rentatools.inventory.application.port.in.CreateToolUseCase;
import com.rentatools.inventory.application.port.in.DeactivateToolUseCase;
import com.rentatools.inventory.application.port.in.GetToolDetailQuery;
import com.rentatools.inventory.application.port.in.ListCategoriesQuery;
import com.rentatools.inventory.application.port.in.ListProviderToolsQuery;
import com.rentatools.inventory.application.port.in.PublishToolUseCase;
import com.rentatools.inventory.application.port.in.RegisterToolUnitUseCase;
import com.rentatools.inventory.application.port.in.RemoveToolImageUseCase;
import com.rentatools.inventory.application.port.in.ReorderToolImagesUseCase;
import com.rentatools.inventory.application.port.in.SearchCatalogQuery;
import com.rentatools.inventory.application.port.in.SetToolRatesUseCase;
import com.rentatools.inventory.application.port.in.UpdateCategoryUseCase;
import com.rentatools.inventory.application.port.in.UpdateToolUseCase;
import com.rentatools.inventory.application.port.in.UploadToolImageUseCase;
import com.rentatools.inventory.domain.exception.NotToolOwnerException;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.inventory.infrastructure.adapter.in.web.AdminCategoryController;
import com.rentatools.inventory.infrastructure.adapter.in.web.ProviderToolController;
import com.rentatools.inventory.infrastructure.adapter.in.web.ProviderToolImageController;
import com.rentatools.inventory.infrastructure.adapter.in.web.ProviderToolUnitController;
import com.rentatools.inventory.infrastructure.adapter.in.web.PublicCatalogController;
import com.rentatools.inventory.infrastructure.adapter.in.web.ToolMediaController;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.port.out.FileStorage;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import com.rentatools.support.DevUsers;
import com.rentatools.support.JwtRequestPostProcessors;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code @WebMvcTest} para los controladores REST de {@code inventory}
 * verificando códigos HTTP 200, 204, 400, 401 y 403.
 */
@WebMvcTest(controllers = {
        PublicCatalogController.class,
        AdminCategoryController.class,
        ProviderToolController.class,
        ProviderToolImageController.class,
        ProviderToolUnitController.class,
        ToolMediaController.class
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
class InventoryControllersWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListCategoriesQuery listCategoriesQuery;

    @MockitoBean
    private SearchCatalogQuery searchCatalogQuery;

    @MockitoBean
    private GetToolDetailQuery getToolDetailQuery;

    @MockitoBean
    private CreateCategoryUseCase createCategoryUseCase;

    @MockitoBean
    private UpdateCategoryUseCase updateCategoryUseCase;

    @MockitoBean
    private CreateToolUseCase createToolUseCase;

    @MockitoBean
    private UpdateToolUseCase updateToolUseCase;

    @MockitoBean
    private SetToolRatesUseCase setToolRatesUseCase;

    @MockitoBean
    private PublishToolUseCase publishToolUseCase;

    @MockitoBean
    private DeactivateToolUseCase deactivateToolUseCase;

    @MockitoBean
    private ListProviderToolsQuery listProviderToolsQuery;

    @MockitoBean
    private UploadToolImageUseCase uploadToolImageUseCase;

    @MockitoBean
    private RemoveToolImageUseCase removeToolImageUseCase;

    @MockitoBean
    private ReorderToolImagesUseCase reorderToolImagesUseCase;

    @MockitoBean
    private RegisterToolUnitUseCase registerToolUnitUseCase;

    @MockitoBean
    private ChangeUnitStatusUseCase changeUnitStatusUseCase;

    @MockitoBean
    private FileStorage fileStorage;

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void publicCatalogAndMediaControllersAllowUnauthenticated200() throws Exception {
        UUID categoryId = Ids.newId();
        UUID toolId = Ids.newId();

        given(listCategoriesQuery.listCategories(true)).willReturn(List.of(
                new CategoryView(categoryId, null, "Eléctricas", "Herramientas eléctricas", true, NOW)
        ));

        given(searchCatalogQuery.searchCatalog(any(), any())).willReturn(PageResult.of(
                List.of(new CatalogToolSummaryView(
                        toolId,
                        DevUsers.PROVIDER_1_ID,
                        "Rotomartillo Bosch",
                        "Bosch",
                        "GBH 2-26",
                        Money.of("150000.00"),
                        Money.of("45000.00"),
                        "/api/v1/media/tools/tools/img0.jpg",
                        Set.of(categoryId),
                        3L
                )),
                0,
                20,
                1L
        ));

        given(getToolDetailQuery.getToolDetail(toolId)).willReturn(new ToolView(
                toolId,
                DevUsers.PROVIDER_1_ID,
                "Rotomartillo Bosch",
                "Bosch",
                "GBH 2-26",
                "Rotomartillo profesional",
                Money.of("150000.00"),
                ToolStatus.ACTIVE,
                Set.of(categoryId),
                Map.of(RatePeriod.DAY, Money.of("45000.00")),
                List.of(),
                3L,
                NOW,
                NOW
        ));

        mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Eléctricas"));

        mockMvc.perform(get("/api/v1/catalog/tools").param("q", "Bosch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Rotomartillo Bosch"))
                .andExpect(jsonPath("$.items[0].dayRate").value(45000.00));

        mockMvc.perform(get("/api/v1/catalog/tools/{id}", toolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(toolId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        byte[] imageBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        given(fileStorage.open("tools/sample.png")).willReturn(new ByteArrayInputStream(imageBytes));

        mockMvc.perform(get("/api/v1/media/tools/tools/sample.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));
    }

    @Test
    void adminCategoryControllerEnforces401And403And400And200() throws Exception {
        UUID categoryId = Ids.newId();

        // 401 sin token
        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Jardinería" }
                                """))
                .andExpect(status().isUnauthorized());

        // 403 con CLIENTE o PROVEEDOR
        mockMvc.perform(post("/api/v1/admin/categories")
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Jardinería" }
                                """))
                .andExpect(status().isForbidden());

        // 400 cuando nombre está vacío
        mockMvc.perform(post("/api/v1/admin/categories")
                        .with(JwtRequestPostProcessors.asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "   " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 200 en POST, PUT y DELETE con ADMIN
        CategoryView createdView = new CategoryView(categoryId, null, "Jardinería", "Equipos de jardín", true, NOW);
        given(createCategoryUseCase.createCategory(any(), any())).willReturn(createdView);
        given(updateCategoryUseCase.updateCategory(any(), any())).willReturn(createdView);
        given(updateCategoryUseCase.deactivateCategory(any(), eq(categoryId))).willReturn(
                new CategoryView(categoryId, null, "Jardinería", "Equipos de jardín", false, NOW)
        );

        mockMvc.perform(post("/api/v1/admin/categories")
                        .with(JwtRequestPostProcessors.asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Jardinería",
                                  "description": "Equipos de jardín"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jardinería"));

        mockMvc.perform(put("/api/v1/admin/categories/{id}", categoryId)
                        .with(JwtRequestPostProcessors.asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Jardinería",
                                  "description": "Equipos de jardín",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", categoryId)
                        .with(JwtRequestPostProcessors.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void providerToolImageAndUnitControllersVerify200And400And401And403() throws Exception {
        UUID toolId = Ids.newId();
        UUID imageId = Ids.newId();
        UUID unitId = Ids.newId();

        // 401 sin token
        mockMvc.perform(post("/api/v1/provider/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Taladro", "depositAmount": 50000 }
                                """))
                .andExpect(status().isUnauthorized());

        // 403 cuando un CLIENTE intenta crear una herramienta
        mockMvc.perform(post("/api/v1/provider/tools")
                        .with(JwtRequestPostProcessors.asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Taladro", "depositAmount": 50000 }
                                """))
                .andExpect(status().isForbidden());

        // 400 cuando falla Bean Validation (nombre vacío o depósito negativo)
        mockMvc.perform(post("/api/v1/provider/tools")
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "", "depositAmount": -10 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 200 al crear herramienta como PROVEEDOR
        ToolView toolView = new ToolView(
                toolId,
                DevUsers.PROVIDER_1_ID,
                "Taladro Percutor",
                "DeWalt",
                "DCD796",
                "Taladro 20V",
                Money.of("100000.00"),
                ToolStatus.DRAFT,
                Set.of(),
                Map.of(RatePeriod.DAY, Money.of("35000.00")),
                List.of(),
                0L,
                NOW,
                NOW
        );
        given(createToolUseCase.createTool(any(), any())).willReturn(toolView);

        mockMvc.perform(post("/api/v1/provider/tools")
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Taladro Percutor",
                                  "brand": "DeWalt",
                                  "model": "DCD796",
                                  "depositAmount": 100000.00,
                                  "rates": [
                                    { "period": "DAY", "price": 35000.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(toolId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // 403 cuando otro proveedor no dueño intenta modificar la herramienta (NotToolOwnerException)
        given(updateToolUseCase.updateTool(any(), any()))
                .willThrow(new NotToolOwnerException("Provider is not the owner of tool " + toolId));

        mockMvc.perform(put("/api/v1/provider/tools/{id}", toolId)
                        .with(JwtRequestPostProcessors.asProvider(DevUsers.PROVIDER_2_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Intento Ajeno",
                                  "depositAmount": 100000.00
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TOOL_OWNER"));

        // 200 al subir imagen multipart
        given(uploadToolImageUseCase.uploadToolImage(any(), any())).willReturn(
                new ToolImageView(imageId, 0, "tools/" + toolId + "/portada.jpg", "image/jpeg", 1024L, NOW)
        );
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "portada.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/v1/provider/tools/{toolId}/images", toolId)
                        .file(multipartFile)
                        .with(JwtRequestPostProcessors.asProvider()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(imageId.toString()))
                .andExpect(jsonPath("$.cover").value(true));

        // 200 al registrar unidad física y cambiar su estado
        given(registerToolUnitUseCase.registerToolUnit(any(), any())).willReturn(
                new ToolUnitView(unitId, toolId, "DW-796-001", UnitStatus.AVAILABLE, LocalDate.of(2026, 2, 1), "Nueva", NOW)
        );
        given(changeUnitStatusUseCase.changeUnitStatus(any(), any())).willReturn(
                new ToolUnitView(unitId, toolId, "DW-796-001", UnitStatus.MAINTENANCE, LocalDate.of(2026, 2, 1), "Mantenimiento", NOW)
        );

        mockMvc.perform(post("/api/v1/provider/tools/{toolId}/units", toolId)
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serialNumber": "DW-796-001",
                                  "acquiredOn": "2026-02-01",
                                  "notes": "Nueva"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value("DW-796-001"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mockMvc.perform(patch("/api/v1/provider/tools/{toolId}/units/{unitId}/status", toolId, unitId)
                        .with(JwtRequestPostProcessors.asProvider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "MAINTENANCE",
                                  "notes": "Mantenimiento"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }
}
