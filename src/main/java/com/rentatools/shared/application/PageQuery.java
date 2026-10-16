package com.rentatools.shared.application;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Parámetros de consulta paginada independientes del framework.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new BusinessRuleViolation("INVALID_PAGE", "page must be >= 0");
        }
        if (size <= 0 || size > MAX_SIZE) {
            throw new BusinessRuleViolation("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }

    public static PageQuery defaultPage() {
        return new PageQuery(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public int offset() {
        return page * size;
    }
}
