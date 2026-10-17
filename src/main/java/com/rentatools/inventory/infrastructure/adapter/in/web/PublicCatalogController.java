package com.rentatools.inventory.infrastructure.adapter.in.web;

import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.port.in.GetToolDetailQuery;
import com.rentatools.inventory.application.port.in.ListCategoriesQuery;
import com.rentatools.inventory.application.port.in.SearchCatalogQuery;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.CatalogToolSummaryResponse;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.CategoryResponse;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.ToolResponse;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST público para navegación y búsqueda en el catálogo de herramientas.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class PublicCatalogController {

    private final ListCategoriesQuery listCategoriesQuery;
    private final SearchCatalogQuery searchCatalogQuery;
    private final GetToolDetailQuery getToolDetailQuery;

    public PublicCatalogController(
            ListCategoriesQuery listCategoriesQuery,
            SearchCatalogQuery searchCatalogQuery,
            GetToolDetailQuery getToolDetailQuery
    ) {
        this.listCategoriesQuery = Objects.requireNonNull(listCategoriesQuery, "listCategoriesQuery must not be null");
        this.searchCatalogQuery = Objects.requireNonNull(searchCatalogQuery, "searchCatalogQuery must not be null");
        this.getToolDetailQuery = Objects.requireNonNull(getToolDetailQuery, "getToolDetailQuery must not be null");
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listActiveCategories() {
        return listCategoriesQuery.listCategories(true).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @GetMapping("/tools")
    public PageResult<CatalogToolSummaryResponse> searchTools(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "minDayPrice", required = false) BigDecimal minDayPrice,
            @RequestParam(name = "maxDayPrice", required = false) BigDecimal maxDayPrice,
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        String searchText = (q != null && !q.isBlank()) ? q : query;
        DateRange availableRange = (startDate != null && endDate != null)
                ? DateRange.of(startDate, endDate)
                : null;

        CatalogSearchCriteria criteria = new CatalogSearchCriteria(
                searchText,
                categoryId,
                minDayPrice != null ? Money.of(minDayPrice) : null,
                maxDayPrice != null ? Money.of(maxDayPrice) : null,
                availableRange
        );

        return searchCatalogQuery.searchCatalog(criteria, PageQuery.of(page, size))
                .map(CatalogToolSummaryResponse::from);
    }

    @GetMapping("/tools/{id}")
    public ToolResponse getToolDetail(@PathVariable UUID id) {
        return ToolResponse.from(getToolDetailQuery.getToolDetail(id));
    }
}
