package com.company.framework.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 컬렉션 공통 유틸 — null 안전 헬퍼 + <b>청크 분할</b>(대량 데이터 배치/분할 전송).
 */
public final class CollectionUtils {

    private CollectionUtils() {}

    public static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> c) {
        return !isEmpty(c);
    }

    public static boolean isEmpty(Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    /** null 이면 빈 불변 리스트. */
    public static <T> List<T> emptyIfNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** 첫 원소(비어있으면 null). */
    public static <T> T firstOrNull(List<T> list) {
        return isEmpty(list) ? null : list.get(0);
    }

    /**
     * 리스트를 최대 {@code size} 크기의 청크들로 나눈다(마지막 청크는 더 작을 수 있음). 대량 INSERT/배치/분할 전송에 사용.
     * 반환 청크는 원본의 뷰가 아니라 복사본(원본 변경에 영향 없음).
     */
    public static <T> List<List<T>> chunk(List<T> list, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size 는 1 이상이어야 합니다.");
        }
        List<List<T>> result = new ArrayList<>();
        if (isEmpty(list)) {
            return result;
        }
        for (int i = 0; i < list.size(); i += size) {
            result.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return result;
    }
}
