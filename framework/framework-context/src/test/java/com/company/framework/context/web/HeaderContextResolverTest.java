package com.company.framework.context.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.context.RequestContext;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("HeaderContextResolver 헤더 기반 해소")
class HeaderContextResolverTest {

    private final HeaderContextResolver resolver = new HeaderContextResolver("X-Tenant-Id", "X-User-Id");

    @Test
    @DisplayName("설정된 헤더에서 tenantId/userId 를 읽는다")
    void readsConfiguredHeaders() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Id", "acme");
        req.addHeader("X-User-Id", "u-1");

        RequestContext ctx = resolver.resolve(req);

        assertThat(ctx.tenantId()).isEqualTo("acme");
        assertThat(ctx.userId()).isEqualTo("u-1");
    }

    @Test
    @DisplayName("Accept-Language 가 있을 때만 locale 을 채운다")
    void localeOnlyWhenHeaderPresent() {
        MockHttpServletRequest withLang = new MockHttpServletRequest();
        withLang.addHeader("Accept-Language", "ko-KR");
        withLang.addPreferredLocale(Locale.KOREA);
        assertThat(resolver.resolve(withLang).locale()).isEqualTo(Locale.KOREA);

        MockHttpServletRequest noLang = new MockHttpServletRequest();
        assertThat(resolver.resolve(noLang).locale()).isNull();
    }

    @Test
    @DisplayName("헤더가 없으면 식별자는 비어 있다")
    void emptyWhenNoHeaders() {
        RequestContext ctx = resolver.resolve(new MockHttpServletRequest());
        assertThat(ctx.hasTenant()).isFalse();
        assertThat(ctx.hasUser()).isFalse();
    }
}
