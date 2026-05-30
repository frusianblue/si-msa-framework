package com.company.framework.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   security:
 *     jwt:
 *       secret: "32바이트 이상 비밀키"
 *       access-token-validity-seconds: 1800       # 30분
 *       refresh-token-validity-seconds: 1209600   # 14일
 */
@ConfigurationProperties(prefix = "framework.security.jwt")
public record JwtProperties(String secret, long accessTokenValiditySeconds, long refreshTokenValiditySeconds) {
    public JwtProperties {
        if (accessTokenValiditySeconds <= 0) accessTokenValiditySeconds = 1800;
        if (refreshTokenValiditySeconds <= 0) refreshTokenValiditySeconds = 1209600;
    }
}
