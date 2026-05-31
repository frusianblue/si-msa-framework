package com.company.framework.secureweb.path;

import com.company.framework.core.error.ErrorCode;
import com.company.framework.secureweb.support.PathSupport;
import com.company.framework.secureweb.support.SecureWebResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 경로 조작(Path Traversal) 차단. 요청 URI/쿼리에서 상위경로 이동(..), 널바이트, 백슬래시,
 * (다중)인코딩 우회 패턴을 탐지하면 400 으로 거부한다.
 *
 * <p>서블릿 컨테이너가 경로의 ".." 를 정규화하더라도, 인코딩된 우회(%2e%2e, %252e 등)와
 * 쿼리 파라미터에 실린 파일경로 조작을 방어한다. (파라미터를 파일명으로 쓰는 다운로드류 보호)
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PathTraversalFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PathTraversalFilter.class);

    private final SecureWebResponder responder;

    public PathTraversalFilter(SecureWebResponder responder) {
        this.responder = responder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isMalicious(request.getRequestURI()) || isMalicious(request.getQueryString())) {
            log.warn(
                    "[secure-web] path-traversal blocked uri={} query={} ip={}",
                    PathSupport.forLog(request.getRequestURI()),
                    PathSupport.forLog(request.getQueryString()),
                    PathSupport.forLog(request.getRemoteAddr()));
            responder.writeError(response, ErrorCode.Common.INVALID_INPUT, "허용되지 않는 경로 패턴이 감지되었습니다.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isMalicious(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String decoded = multiDecode(value).toLowerCase(Locale.ROOT);
        if (decoded.indexOf('\0') >= 0 || decoded.contains("%00")) {
            return true; // 널바이트
        }
        if (decoded.contains("..")) {
            return true; // 상위경로 이동(정규 REST 경로엔 불필요)
        }
        // 인코딩이 남아있는 경우까지 보강 (multiDecode 가 못 푼 형태)
        return decoded.contains("%2e%2e") || decoded.contains("..%2f") || decoded.contains("..%5c");
    }

    /** 다중 인코딩 우회를 풀기 위해 최대 2회 디코딩(변화 없으면 중단). */
    private String multiDecode(String value) {
        String current = value;
        for (int i = 0; i < 2; i++) {
            String next = tryDecode(current);
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    private String tryDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value; // 잘못된 인코딩이면 원본 유지(원본 검사로 충분)
        }
    }
}
