package com.company.framework.core.page;

import com.company.framework.core.util.SecureUtils;

import java.util.Set;

/**
 * 목록 API 공통 검색조건. 정렬은 화이트리스트로 검증해 SQL 인젝션을 차단한다.
 *   ?page=1&size=20&sortBy=createdAt&sortDirection=DESC&keyword=홍
 */
public class SearchCondition {
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
    private String keyword;

    public PageRequest toPageRequest() {
        return PageRequest.of(page, size);
    }

    /**
     * 허용된 정렬 컬럼만 통과시켜 안전한 "컬럼 방향" 문자열을 만든다. (MyBatis ${orderBy} 용)
     * @param allowed 허용 컬럼 화이트리스트 (예: Set.of("id","createdAt","name"))
     * @param defaultColumn 미지정/비허용 시 사용할 기본 컬럼
     */
    public String toSafeOrderBy(Set<String> allowed, String defaultColumn) {
        String col = (sortBy == null || sortBy.isBlank()) ? defaultColumn
                : SecureUtils.safeOrderColumn(sortBy, allowed);
        String dir = SecureUtils.safeOrderDirection(sortDirection);
        // camelCase -> snake_case (MyBatis 컬럼 규칙)
        String snake = col.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return snake + " " + dir;
    }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
