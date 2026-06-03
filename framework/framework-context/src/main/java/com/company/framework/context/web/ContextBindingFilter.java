package com.company.framework.context.web;

import com.company.framework.context.ContextHolder;
import com.company.framework.context.RequestContext;
import com.company.framework.context.config.ContextProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 {@link RequestContext} 를 바인딩하고 종료 시 정리하는 필터.
 *
 * <p>{@code MdcTraceFilter}(HIGHEST_PRECEDENCE, traceId) 바로 안쪽에서 동작하도록 우선순위를 약간 낮춘다
 * (traceId 가 이미 MDC 에 있는 상태에서 tenantId/userId 를 덧붙임). {@code putToMdc=true} 면 식별정보를
 * MDC 에 심어 로그에 자동 노출하고, finally 에서 자기 MDC 키와 컨텍스트를 모두 정리한다(스레드 재사용 누수 방지).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ContextBindingFilter extends OncePerRequestFilter {

    private final ContextResolver resolver;
    private final ContextProperties props;

    public ContextBindingFilter(ContextResolver resolver, ContextProperties props) {
        this.resolver = resolver;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestContext ctx = resolver.resolve(request);
        ContextHolder.set(ctx);
        boolean mdc = props.isPutToMdc();
        if (mdc) {
            putMdc(props.getMdcTenantKey(), ctx.tenantId());
            putMdc(props.getMdcUserKey(), ctx.userId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (mdc) {
                MDC.remove(props.getMdcTenantKey());
                MDC.remove(props.getMdcUserKey());
            }
            ContextHolder.clear();
        }
    }

    private static void putMdc(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
