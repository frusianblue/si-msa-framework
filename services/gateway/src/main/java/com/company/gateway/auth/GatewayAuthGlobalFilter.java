package com.company.gateway.auth;

import com.company.gateway.config.GatewayAuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 게이트웨이 엣지 인증. 모든 라우팅/레이트리밋보다 먼저 실행되어:
 *
 * <ol>
 *   <li><b>스푸핑 차단</b>: 클라이언트가 보낸 신뢰 헤더(X-User-Id/X-User-Roles/X-Tenant-Id)를 항상 제거.
 *   <li><b>화이트리스트</b>(인증/액추에이터/폴백)는 토큰 없이 통과.
 *   <li>그 외 경로는 Bearer JWT 를 검증(서명+만료+typ). 실패 시 401(ApiResponse.fail 형식 JSON).
 *       <b>이중 발급기</b>: 토큰의 {@code iss} 로 분기 — AS issuer 면 RS256/JWKS, 아니면 자체 JWT(HMAC).
 *       (AUTH_SERVER.md §4. AS 검증은 {@code gateway.auth.authorization-server.enabled=true} 일 때만 활성.)
 *   <li><b>중앙 로그아웃</b>: jti 가 블랙리스트(공유 저장소)면 차단(논블로킹). 검사 비활성 시 생략.
 *       단 <b>자체 JWT(INTERNAL)에만</b> 적용 — AS 토큰 폐기는 AS {@code /oauth2/revoke} 경로다(혼용 금지, §4).
 *   <li>성공 시 토큰의 sub/roles 를 <b>신뢰 헤더로 주입</b>해 다운스트림에 전달(Authorization 은 그대로 유지).
 *       검증한 userId 를 exchange 속성에 심어 레이트리밋 KeyResolver 가 사용자 기준으로 동작하게 한다.
 * </ol>
 */
public class GatewayAuthGlobalFilter implements GlobalFilter, Ordered {

    /** RateLimit KeyResolver 가 읽는 exchange 속성 키(검증된 userId). */
    public static final String USER_ID_ATTRIBUTE = "gatewayUserId";

    private final GatewayTokenAuthenticator authenticator;
    private final GatewayTokenBlacklist blacklist;
    private final List<String> permitAllPatterns;
    private final String userIdHeader;
    private final String rolesHeader;
    private final String tenantHeader;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public GatewayAuthGlobalFilter(
            GatewayTokenAuthenticator authenticator, GatewayTokenBlacklist blacklist, GatewayAuthProperties props) {
        this.authenticator = authenticator;
        this.blacklist = blacklist;
        this.permitAllPatterns = props.getPermitAllPatterns();
        this.userIdHeader = props.getUserIdHeader();
        this.rolesHeader = props.getRolesHeader();
        this.tenantHeader = props.getTenantHeader();
    }

    @Override
    public int getOrder() {
        return -100; // 라우팅/레이트리밋/로드밸런서보다 앞
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        // (1) 스푸핑 차단 — 클라이언트가 직접 보낸 신뢰 헤더는 무조건 제거(공개 경로 포함).
        ServerHttpRequest.Builder mutated = request.mutate().headers(h -> {
            h.remove(userIdHeader);
            h.remove(rolesHeader);
            h.remove(tenantHeader);
        });

        // (2) 화이트리스트는 토큰 없이 통과.
        if (isPermitAll(path)) {
            return chain.filter(exchange.mutate().request(mutated.build()).build());
        }

        // (3) Bearer 추출/검증 — iss 로 분기(자체 JWT=HMAC / AS=RS256·JWKS).
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange, "인증 토큰이 필요합니다.");
        }
        String token = authorization.substring(7).trim();
        TokenIssuerKind kind = authenticator.kindOf(token); // 순수 CPU(IO 없음) — 이벤트 루프 안전

        // AS 경로만 JWKS 조회(블로킹 IO 가능) → boundedElastic 으로 오프로드. 자체 JWT 핫패스는 그대로 이벤트 루프에서.
        Mono<GatewayTokenVerifier.Verified> verifiedMono = (kind == TokenIssuerKind.AUTHORIZATION_SERVER)
                ? Mono.fromCallable(() -> authenticator.verify(token, kind)).subscribeOn(Schedulers.boundedElastic())
                : Mono.fromCallable(() -> authenticator.verify(token, kind));

        return verifiedMono
                .flatMap(verified -> afterVerified(exchange, chain, mutated, verified, kind))
                .onErrorResume(RuntimeException.class, e -> unauthorized(exchange, "유효하지 않은 토큰입니다."));
    }

    /** 검증 성공 후: (4) 중앙 로그아웃 검사(자체 JWT 한정) → (5) 신뢰 헤더 주입 + 레이트리밋 연동. */
    private Mono<Void> afterVerified(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest.Builder mutated,
            GatewayTokenVerifier.Verified verified,
            TokenIssuerKind kind) {
        // (4) 중앙 로그아웃: 자체 JWT(INTERNAL)의 jti 만 블랙리스트 대조. AS 토큰 폐기는 AS /oauth2/revoke (혼용 금지, §4).
        Mono<Boolean> revokedCheck =
                (kind == TokenIssuerKind.INTERNAL) ? blacklist.isBlacklisted(verified.jti()) : Mono.just(Boolean.FALSE);

        return revokedCheck.flatMap(revoked -> {
            if (Boolean.TRUE.equals(revoked)) {
                return unauthorized(exchange, "로그아웃된 토큰입니다.");
            }
            // (5) 신뢰 헤더 주입 + 레이트리밋 연동.
            mutated.header(userIdHeader, verified.userId());
            if (!verified.roles().isEmpty()) {
                mutated.header(rolesHeader, String.join(",", verified.roles()));
            }
            exchange.getAttributes().put(USER_ID_ATTRIBUTE, verified.userId());
            return chain.filter(exchange.mutate().request(mutated.build()).build());
        });
    }

    private boolean isPermitAll(String path) {
        for (String pattern : permitAllPatterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // core 의존이 없으므로 ApiResponse.fail 형식을 수기 JSON 으로 맞춘다(고정 문자열 — 이스케이프 불필요).
        String body = "{\"success\":false,\"code\":\"E0401\",\"message\":\"" + message + "\",\"timestamp\":\""
                + Instant.now() + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
