package com.company.gateway.config;

import com.company.gateway.auth.GatewayAuthGlobalFilter;
import java.net.InetSocketAddress;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * RequestRateLimiter 용 키 산출. 인증 주체(Principal)가 있으면 주체 기준, 없으면 클라이언트 IP 로 강등한다.
 * SpEL {@code #{@principalKeyResolver}} 로 라우트 필터에서 참조한다.
 *
 * <p>키를 절대 비우지 않으므로 RequestRateLimiter 의 deny-empty-key(기본 true) 정책에 걸리지 않는다.
 * 게이트웨이가 L7 로드밸런서/Ingress 뒤에 있을 수 있어 X-Forwarded-For 첫 홉을 우선 사용하고,
 * 없으면 원격 주소로 폴백한다(신뢰 프록시 설정은 application.yml 의 trusted-proxies 참고).
 */
@Configuration
public class RateLimitConfiguration {

    @Bean
    public KeyResolver principalKeyResolver() {
        return exchange -> {
            // 엣지 인증 필터가 검증한 userId 가 있으면 사용자 기준(없으면 Principal → IP 로 강등).
            String verifiedUserId = exchange.getAttribute(GatewayAuthGlobalFilter.USER_ID_ATTRIBUTE);
            if (StringUtils.hasText(verifiedUserId)) {
                return Mono.just("u:" + verifiedUserId);
            }
            return exchange.getPrincipal()
                    .map(Principal::getName)
                    .filter(StringUtils::hasText)
                    .map(name -> "u:" + name)
                    .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
        };
    }

    private String clientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
