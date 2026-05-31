package com.company.framework.secureweb.injection;

import com.company.framework.core.error.ErrorCode;
import com.company.framework.secureweb.config.SecureWebProperties;
import com.company.framework.secureweb.support.PathSupport;
import com.company.framework.secureweb.support.SecureWebResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 인젝션 스크리닝(주로 SQLi). 쿼리스트링과 요청 파라미터 값에서 고전적 공격 시그니처를 탐지한다.
 * <b>기본 off</b> — 오탐 가능성이 있어 명시적으로 켜야 한다. 진짜 방어는 파라미터화 쿼리(MyBatis #{}).
 *
 * <ul>
 *   <li>mode=BLOCK: 탐지 시 400 거부</li>
 *   <li>mode=LOG_ONLY: 탐지만 경고 로그로 남기고 통과(룰 튜닝/관찰용)</li>
 * </ul>
 *
 * <p>JSON 본문은 스트림 버퍼링 비용/위험 때문에 의도적으로 검사 대상에서 제외한다(파라미터·쿼리만).
 * 폼 파라미터 조회는 컨테이너 캐시를 쓰므로 다운스트림 @RequestBody/getParameter 에 영향 없다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class InjectionScreeningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InjectionScreeningFilter.class);

    /** 보수적으로 선별한 내장 패턴(오탐 최소화 지향). */
    private static final List<String> BUILT_IN = List.of(
            "(?i)\\bunion\\b\\s+(all\\s+)?\\bselect\\b", // UNION SELECT
            "(?i)(\\bor\\b|\\band\\b)\\s+['\"\\d]+\\s*=\\s*['\"\\d]+", // OR 1=1 / AND '1'='1'
            "(?i);\\s*(drop|alter|truncate|delete|update|insert|create)\\b", // 스택 쿼리
            "(?i)\\b(drop|truncate)\\s+table\\b",
            "(?i)(sleep\\s*\\(|benchmark\\s*\\(|waitfor\\s+delay|pg_sleep\\s*\\()", // 시간기반
            "(?i)(information_schema|sysobjects|xp_cmdshell)\\b");

    private final SecureWebResponder responder;
    private final SecureWebProperties.Injection cfg;
    private final List<Pattern> patterns;

    public InjectionScreeningFilter(SecureWebResponder responder, SecureWebProperties.Injection cfg) {
        this.responder = responder;
        this.cfg = cfg;
        List<Pattern> compiled = new ArrayList<>();
        for (String p : BUILT_IN) {
            compiled.add(Pattern.compile(p));
        }
        if (cfg.getAdditionalPatterns() != null) {
            for (String p : cfg.getAdditionalPatterns()) {
                if (p != null && !p.isBlank()) {
                    compiled.add(Pattern.compile(p));
                }
            }
        }
        this.patterns = List.copyOf(compiled);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = PathSupport.relativePath(request);
        if (PathSupport.matchesAny(cfg.getExcludePaths(), path)) {
            chain.doFilter(request, response);
            return;
        }

        String hit = firstSuspiciousValue(request);
        if (hit != null) {
            if (cfg.getMode() == SecureWebProperties.Injection.Mode.LOG_ONLY) {
                log.warn(
                        "[secure-web] injection pattern detected (log-only) path={} value={} ip={}",
                        PathSupport.forLog(path),
                        PathSupport.forLog(hit),
                        PathSupport.forLog(request.getRemoteAddr()));
            } else {
                log.warn(
                        "[secure-web] injection blocked path={} value={} ip={}",
                        PathSupport.forLog(path),
                        PathSupport.forLog(hit),
                        PathSupport.forLog(request.getRemoteAddr()));
                responder.writeError(response, ErrorCode.Common.INVALID_INPUT, "허용되지 않는 입력 패턴이 감지되었습니다.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** 쿼리스트링(디코딩) + 모든 파라미터 값을 검사해 최초로 매칭된 값을 반환(없으면 null). */
    private String firstSuspiciousValue(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query != null) {
            String decoded = tryDecode(query);
            if (matches(decoded)) {
                return decoded;
            }
        }
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            for (String value : e.getValue()) {
                if (matches(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean matches(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    private String tryDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
