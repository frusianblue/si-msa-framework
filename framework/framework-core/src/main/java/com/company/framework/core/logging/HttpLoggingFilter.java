package com.company.framework.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 요청/응답 본문을 로깅하되, 민감 필드는 마스킹하고 본문 크기를 제한한다.
 * - 비밀번호/토큰/주민번호/카드번호 등은 정규식으로 마스킹
 * - 멀티파트/대용량 바디는 로깅 생략
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);
    private static final int MAX_BODY = 2000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, MAX_BODY);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(req, res);
        } finally {
            long took = System.currentTimeMillis() - start;
            if (log.isInfoEnabled()) {
                log.info(
                        ">> {} {} body={}",
                        req.getMethod(),
                        req.getRequestURI(),
                        SensitiveDataMasker.mask(body(req.getContentAsByteArray())));
                log.info(
                        "<< {} {} status={} {}ms body={}",
                        req.getMethod(),
                        req.getRequestURI(),
                        res.getStatus(),
                        took,
                        SensitiveDataMasker.mask(body(res.getContentAsByteArray())));
            }
            res.copyBodyToResponse();
        }
    }

    private boolean isSkip(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String ct = req.getContentType();
        return uri.startsWith("/actuator")
                || uri.contains("/swagger")
                || uri.contains("/v3/api-docs")
                || (ct != null && ct.startsWith("multipart/"));
    }

    private String body(byte[] buf) {
        if (buf == null || buf.length == 0) return "";
        int len = Math.min(buf.length, MAX_BODY);
        String s = new String(buf, 0, len, StandardCharsets.UTF_8);
        return buf.length > MAX_BODY ? s + "...(truncated)" : s;
    }
}
