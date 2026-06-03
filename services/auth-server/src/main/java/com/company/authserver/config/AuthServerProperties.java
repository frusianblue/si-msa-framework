package com.company.authserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authorization Server 설정.
 *
 * @param issuer 발급자 식별자(iss). 토큰/discovery 에 박히므로 외부에서 접근 가능한 안정 URL 이어야 한다. 예: https://auth.example.com
 * @param jwkCacheTtl JWKSource 가 DB 에서 읽은 키 스냅샷을 캐시하는 시간(파드 간 회전 전파 지연 = 최대 이 값).
 */
@ConfigurationProperties(prefix = "auth-server")
public record AuthServerProperties(String issuer, Duration jwkCacheTtl) {

    public AuthServerProperties {
        if (issuer == null || issuer.isBlank()) {
            // discovery/토큰 iss 가 비면 RP 검증이 전부 깨진다 → 기동 시점에 막는다.
            throw new IllegalStateException("auth-server.issuer 는 필수입니다(외부 접근 가능한 안정 URL).");
        }
        if (jwkCacheTtl == null || jwkCacheTtl.isZero() || jwkCacheTtl.isNegative()) {
            jwkCacheTtl = Duration.ofMinutes(5);
        }
    }
}
