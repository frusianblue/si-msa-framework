package com.company.framework.core.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 시큐어코딩 공통 유틸. (행안부 SW 개발보안 가이드 대응)
 *  - 경로조작(Path Traversal) 방어: 파일명 정제
 *  - SQL 인젝션 방어: 동적 정렬(ORDER BY)에 쓰일 컬럼명 화이트리스트 검증
 */
public final class SecureUtils {

    private SecureUtils() {}

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

    /** 업로드 파일명에서 경로 구분자/상위 경로 제거 (../ 차단) */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) return null;
        String name = fileName.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1); // 디렉터리 부분 제거
        name = name.replace("..", "").replaceAll("[\\x00-\\x1f]", "").trim();
        return name.isBlank() ? "unnamed" : name;
    }

    /**
     * MyBatis 에서 ${} 로 컬럼/정렬 방향을 받아야 할 때, 허용 목록에 있는 값만 통과.
     * (#{} 로 바인딩 불가능한 ORDER BY 컬럼명 등에 사용)
     */
    public static String safeOrderColumn(String column, Set<String> allowed) {
        if (column == null || !SAFE_IDENTIFIER.matcher(column).matches() || !allowed.contains(column)) {
            throw new IllegalArgumentException("허용되지 않은 정렬 컬럼입니다: " + column);
        }
        return column;
    }

    /** 정렬 방향 검증 (ASC/DESC 만 허용) */
    public static String safeOrderDirection(String direction) {
        if (direction == null) return "ASC";
        String d = direction.trim().toUpperCase();
        return ("ASC".equals(d) || "DESC".equals(d)) ? d : "ASC";
    }
}
