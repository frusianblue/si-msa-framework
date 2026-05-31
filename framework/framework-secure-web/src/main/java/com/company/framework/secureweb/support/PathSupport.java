package com.company.framework.secureweb.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

/**
 * 필터 공통 보조: 컨텍스트패스 제거한 앱 상대 경로, Ant 패턴 매칭, 로그 안전 문자열.
 */
public final class PathSupport {

    private static final PathMatcher MATCHER = new AntPathMatcher();

    private PathSupport() {}

    /** 컨텍스트패스를 제거한 앱 상대 경로(매칭/예외 비교 기준). */
    public static String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            String rest = uri.substring(ctx.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return uri;
    }

    /** patterns 중 하나라도 path 에 매칭되면 true. Ant 패턴(/api/**) 과 prefix 모두 지원. */
    public static boolean matchesAny(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (MATCHER.isPattern(pattern)) {
                if (MATCHER.match(pattern, path)) {
                    return true;
                }
            } else if (path.equals(pattern) || path.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** 로그 주입(CRLF) 방지 + 과도한 길이 절단. */
    public static String forLog(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 256 ? cleaned.substring(0, 256) + "..." : cleaned;
    }
}
