package com.company.framework.core.page;

import java.util.List;

/**
 * 공통 페이징 응답.
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    public static <T> PageResponse<T> of(List<T> content, PageRequest req, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / req.size());
        boolean hasNext = req.page() < totalPages;
        return new PageResponse<>(content, req.page(), req.size(), totalElements, totalPages, hasNext);
    }
}
