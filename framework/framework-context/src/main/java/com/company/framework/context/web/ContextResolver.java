package com.company.framework.context.web;

import com.company.framework.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청에서 {@link RequestContext} 를 만드는 해소 전략. 기본 구현은 {@link HeaderContextResolver}(헤더 기반).
 *
 * <p>앱이 같은 타입 빈을 직접 정의하면 그쪽이 우선({@code @ConditionalOnMissingBean}) — 예: JWT 클레임이나
 * Spring Security 의 {@code SecurityContext} 에서 테넌트/사용자를 뽑는 구현으로 교체.
 */
public interface ContextResolver {

    /** 요청에서 컨텍스트를 추출(식별정보가 없으면 {@link RequestContext#EMPTY} 반환 가능). */
    RequestContext resolve(HttpServletRequest request);
}
