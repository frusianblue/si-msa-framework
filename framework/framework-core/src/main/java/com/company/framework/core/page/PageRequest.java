package com.company.framework.core.page;

/**
 * 공통 페이징 요청 파라미터. (MyBatis 환경에서 offset 계산을 표준화)
 */
public record PageRequest(int page, int size) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    public PageRequest {
        if (page < 1) page = DEFAULT_PAGE;
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
    }

    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(page == null ? DEFAULT_PAGE : page,
                               size == null ? DEFAULT_SIZE : size);
    }

    /** MyBatis LIMIT/OFFSET 용 */
    public int offset() {
        return (page - 1) * size;
    }
}
