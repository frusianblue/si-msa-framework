package com.company.framework.context.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.context.ContextHolder;
import com.company.framework.context.RequestContext;
import com.company.framework.context.config.ContextProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("ContextBindingFilter 요청 바인딩/정리")
class ContextBindingFilterTest {

    private final ContextProperties props = new ContextProperties();
    private final ContextResolver resolver = new HeaderContextResolver("X-Tenant-Id", "X-User-Id");
    private final ContextBindingFilter filter = new ContextBindingFilter(resolver, props);

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("체인 실행 중에는 컨텍스트와 MDC 가 바인딩되고, 종료 후 정리된다")
    void bindsDuringChainAndClearsAfter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Id", "acme");
        req.addHeader("X-User-Id", "u-1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] insideTenant = new String[1];
        String[] insideMdcTenant = new String[1];
        FilterChain chain = (request, response) -> {
            insideTenant[0] = ContextHolder.get().tenantId();
            insideMdcTenant[0] = MDC.get("tenantId");
        };

        filter.doFilter(req, res, chain);

        // 체인 안에서 바인딩됨
        assertThat(insideTenant[0]).isEqualTo("acme");
        assertThat(insideMdcTenant[0]).isEqualTo("acme");
        // 종료 후 정리됨
        assertThat(ContextHolder.get()).isSameAs(RequestContext.EMPTY);
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @DisplayName("put-to-mdc=false 면 MDC 에 식별정보를 심지 않는다")
    void skipsMdcWhenDisabled() throws Exception {
        props.setPutToMdc(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Id", "acme");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] insideMdc = new String[1];
        FilterChain chain = (request, response) -> insideMdc[0] = MDC.get("tenantId");
        filter.doFilter(req, res, chain);

        assertThat(insideMdc[0]).isNull();
    }

    @Test
    @DisplayName("체인이 예외를 던져도 컨텍스트는 정리된다")
    void clearsEvenOnException() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Id", "acme");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain boom = (request, response) -> {
            throw new RuntimeException("boom");
        };

        try {
            filter.doFilter(req, res, boom);
        } catch (Exception ignored) {
            // 예외 전파는 컨테이너가 처리
        }
        assertThat(ContextHolder.get()).isSameAs(RequestContext.EMPTY);
    }
}
