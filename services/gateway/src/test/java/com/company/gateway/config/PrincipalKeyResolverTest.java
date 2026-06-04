package com.company.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.gateway.auth.GatewayAuthGlobalFilter;
import java.net.InetSocketAddress;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 레이트리밋(RequestRateLimiter) 키 산출기 단위 테스트. 게이트웨이 기동/Redis 없이 KeyResolver 만 직접 호출한다.
 *
 * <p>우선순위 검증: (1) 엣지 인증이 심은 검증 userId → (2) Principal → (3) XFF 첫 홉 → (4) remote addr → (5) unknown.
 * 키는 절대 비지 않아 RequestRateLimiter 의 deny-empty-key(기본 true) 정책에 걸리지 않는다.
 */
class PrincipalKeyResolverTest {

    private final KeyResolver resolver = new RateLimitConfiguration().principalKeyResolver();

    private String resolve(ServerWebExchange exchange) {
        return resolver.resolve(exchange).block();
    }

    @Test
    void verified_user_id_takes_priority_over_everything() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("X-Forwarded-For", "203.0.113.9")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345)));
        exchange.getAttributes().put(GatewayAuthGlobalFilter.USER_ID_ATTRIBUTE, "user-7");

        assertThat(resolve(exchange)).isEqualTo("u:user-7");
    }

    @Test
    void falls_back_to_principal_when_no_verified_user() {
        Principal principal = () -> "alice";
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                        .remoteAddress(new InetSocketAddress("10.0.0.1", 12345)))
                .mutate()
                .principal(Mono.just(principal))
                .build();

        assertThat(resolve(exchange)).isEqualTo("u:alice");
    }

    @Test
    void uses_xff_first_hop_when_anonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("X-Forwarded-For", "203.0.113.9, 70.41.3.18, 150.172.238.178")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345)));

        assertThat(resolve(exchange)).isEqualTo("ip:203.0.113.9");
    }

    @Test
    void falls_back_to_remote_addr_when_no_xff() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").remoteAddress(new InetSocketAddress("10.0.0.1", 12345)));

        assertThat(resolve(exchange)).isEqualTo("ip:10.0.0.1");
    }

    @Test
    void malformed_leading_comma_xff_falls_back_to_remote_addr() {
        // 선행 콤마 기형/위조 XFF 는 첫 토큰이 비므로 remote addr 로 폴백한다(원문 통째가 키에 새지 않음).
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("X-Forwarded-For", ", 70.41.3.18")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345)));

        assertThat(resolve(exchange)).isEqualTo("ip:10.0.0.1");
    }

    @Test
    void unknown_when_neither_principal_nor_address_present() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me"));

        String key = resolve(exchange);
        assertThat(key).isEqualTo("ip:unknown");
        // deny-empty-key 안전: 키는 비거나 prefix 만으로 끝나지 않는다.
        assertThat(key).isNotBlank().isNotEqualTo("ip:").isNotEqualTo("u:");
    }
}
