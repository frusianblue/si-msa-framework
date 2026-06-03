package com.company.framework.context.web;

import com.company.framework.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * 헤더 기반 기본 {@link ContextResolver}.
 *
 * <ul>
 *   <li>tenantId ← {@code tenantHeader}(기본 {@code X-Tenant-Id})
 *   <li>userId ← {@code userHeader}(기본 {@code X-User-Id})
 *   <li>locale ← {@code Accept-Language} 가 있을 때만 {@code request.getLocale()} (없으면 null 로 둬 서버 기본 로케일 오인 방지)
 * </ul>
 *
 * <p>게이트웨이/상위 서비스가 신뢰 헤더로 식별정보를 내려준다는 전제. 외부 경계에서는 헤더 위조 방지를
 * 게이트웨이/인증 계층이 책임진다(본 리졸버는 신뢰 헤더를 읽기만 한다).
 */
public class HeaderContextResolver implements ContextResolver {

    private final String tenantHeader;
    private final String userHeader;

    public HeaderContextResolver(String tenantHeader, String userHeader) {
        this.tenantHeader = tenantHeader;
        this.userHeader = userHeader;
    }

    @Override
    public RequestContext resolve(HttpServletRequest request) {
        Locale locale = (request.getHeader("Accept-Language") != null) ? request.getLocale() : null;
        return RequestContext.builder()
                .tenantId(request.getHeader(tenantHeader))
                .userId(request.getHeader(userHeader))
                .locale(locale)
                .build();
    }
}
