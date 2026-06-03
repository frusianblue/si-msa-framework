package com.company.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 엣지 인증 필터 + 중앙 로그아웃(블랙리스트) 동작 단위 테스트. 실제 게이트웨이 기동 없이 MockServerWebExchange 로
 * 검증한다(블랙리스트는 가짜 구현 주입 — Redis 불요).
 */
class GatewayAuthGlobalFilterTest {

    private static final String SECRET = "0123456789012345678901234567890123456789"; // 40 bytes
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final GatewayTokenVerifier verifier = new GatewayTokenVerifier(SECRET, "access");
    private final GatewayAuthProperties props = new GatewayAuthProperties();

    private String accessToken(String jti, String subject, List<String> roles) {
        return Jwts.builder()
                .id(jti)
                .subject(subject)
                .claim("roles", roles)
                .claim("typ", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();
    }

    private GatewayFilterChain recordingChain(AtomicBoolean called, AtomicReference<ServerWebExchange> captured) {
        return exchange -> {
            called.set(true);
            captured.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void blacklisted_token_is_rejected_with_401_and_chain_not_called() {
        GatewayTokenBlacklist blacklist = jti -> Mono.just(true);
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(verifier, blacklist, props);
        String token = accessToken("jti-revoked", "user-1", List.of("USER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(chainCalled, new AtomicReference<>()))
                .block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void valid_non_blacklisted_token_passes_and_injects_trusted_headers() {
        GatewayTokenBlacklist blacklist = jti -> Mono.just(false);
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(verifier, blacklist, props);
        String token = accessToken("jti-live", "user-7", List.of("ADMIN"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, recordingChain(chainCalled, captured)).block();

        assertThat(chainCalled).isTrue();
        assertThat(captured.get().getRequest().getHeaders().getFirst(props.getUserIdHeader()))
                .isEqualTo("user-7");
        assertThat(captured.get().getRequest().getHeaders().getFirst(props.getRolesHeader()))
                .isEqualTo("ADMIN");
    }

    @Test
    void permit_all_path_skips_auth_even_if_blacklist_would_reject() {
        GatewayTokenBlacklist blacklist = jti -> Mono.just(true); // 화이트리스트면 토큰/블랙리스트 무관 통과
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(verifier, blacklist, props);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(chainCalled, new AtomicReference<>()))
                .block();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void missing_bearer_is_rejected_with_401() {
        GatewayTokenBlacklist blacklist = jti -> Mono.just(false);
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(verifier, blacklist, props);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(chainCalled, new AtomicReference<>()))
                .block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void client_supplied_trusted_headers_are_stripped() {
        GatewayTokenBlacklist blacklist = jti -> Mono.just(false);
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(verifier, blacklist, props);
        String token = accessToken("jti-live", "real-user", List.of("USER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(props.getUserIdHeader(), "spoofed-admin") // 위조 시도
                .header(props.getRolesHeader(), "ADMIN"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, recordingChain(new AtomicBoolean(false), captured))
                .block();

        // 위조 헤더는 제거되고 토큰에서 검증된 값으로만 채워진다.
        assertThat(captured.get().getRequest().getHeaders().getFirst(props.getUserIdHeader()))
                .isEqualTo("real-user");
        assertThat(captured.get().getRequest().getHeaders().getFirst(props.getRolesHeader()))
                .isEqualTo("USER");
    }
}
