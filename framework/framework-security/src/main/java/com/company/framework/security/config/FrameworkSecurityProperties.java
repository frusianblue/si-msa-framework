package com.company.framework.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   security:
 *     enabled: true                # 보안 자동설정 전체 on/off
 *     dynamic-authorization: true  # DB기반 동적 인가(false면 '인증만 되면 통과')
 *     menu: true                   # 메뉴 API 활성화
 *     edge-trust:                  # 다운스트림 신뢰 자세(배치 환경별, TOKEN_VERIFICATION_GUIDE.md §7)
 *       mode: zero-trust           #   zero-trust(기본, Bearer 재검증) | gateway-headers(게이트웨이 헤더 신뢰)
 *       user-id-header: X-User-Id  #   gateway-headers 모드에서 신원을 읽을 헤더(게이트웨이 주입과 일치)
 *       roles-header: X-User-Roles
 *     resource-server:             # AS(OP) 발급 RS256/JWKS 토큰 재검증(zero-trust 에서 사용, 기본 off)
 *       enabled: false
 *       issuer: ${AUTH_SERVER_ISSUER:}
 *       jwks-uri: ${AUTH_SERVER_JWKS_URI:}   # 생략 시 {issuer}/oauth2/jwks
 *       roles-claim: roles
 *       audience: ${RESOURCE_SERVER_AUDIENCE:}   # 비면 aud 검증 생략
 *       clock-skew: 60s
 *       jwk-cache-ttl: 1h
 */
@ConfigurationProperties(prefix = "framework.security")
public class FrameworkSecurityProperties {
    private boolean enabled = true;
    private boolean dynamicAuthorization = true;
    private boolean menu = true;

    private final EdgeTrust edgeTrust = new EdgeTrust();
    private final ResourceServer resourceServer = new ResourceServer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDynamicAuthorization() {
        return dynamicAuthorization;
    }

    public void setDynamicAuthorization(boolean v) {
        this.dynamicAuthorization = v;
    }

    public boolean isMenu() {
        return menu;
    }

    public void setMenu(boolean menu) {
        this.menu = menu;
    }

    public EdgeTrust getEdgeTrust() {
        return edgeTrust;
    }

    public ResourceServer getResourceServer() {
        return resourceServer;
    }

    /**
     * 다운스트림 신뢰 자세. 신뢰 경계를 네트워크가 보장하는 정도에 따라 환경별(프로파일)로 바꾼다.
     *
     * <ul>
     *   <li>{@code zero-trust}(기본) — Bearer 를 로컬 재검증. VM 등 네트워크 격리가 약한 환경 권장(안전 기본값).
     *   <li>{@code gateway-headers} — 게이트웨이가 검증·주입한 헤더를 신뢰(Bearer 재검증 생략). K8s + NetworkPolicy 등
     *       "게이트웨이 우회 불가"가 네트워크로 보장될 때만 안전 → <b>의도적 옵트인</b>이어야 한다.
     * </ul>
     */
    public static class EdgeTrust {
        private Mode mode = Mode.ZERO_TRUST;
        private String userIdHeader = "X-User-Id";
        private String rolesHeader = "X-User-Roles";

        public enum Mode {
            ZERO_TRUST,
            GATEWAY_HEADERS
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getUserIdHeader() {
            return userIdHeader;
        }

        public void setUserIdHeader(String userIdHeader) {
            this.userIdHeader = userIdHeader;
        }

        public String getRolesHeader() {
            return rolesHeader;
        }

        public void setRolesHeader(String rolesHeader) {
            this.rolesHeader = rolesHeader;
        }
    }

    /**
     * 리소스 서버 — AS(OP) 발급 RS256 토큰을 JWKS 로 다운스트림에서 재검증(이중 발급기, AUTH_SERVER.md §4).
     * {@code zero-trust} 모드에서 AS 토큰까지 무신뢰 재검증하려면 켠다. 기본 off — 켜기 전에는 자체 JWT(HMAC)만 재검증.
     */
    public static class ResourceServer {
        private boolean enabled = false;
        private String issuer;
        private String jwksUri;
        private String rolesClaim = "roles";
        private String audience;
        private Duration clockSkew = Duration.ofSeconds(60);
        private Duration jwkCacheTtl = Duration.ofHours(1);

        /** jwks-uri 가 비면 issuer 로부터 표준 경로({issuer}/oauth2/jwks)를 구성. */
        public String resolvedJwksUri() {
            if (jwksUri != null && !jwksUri.isBlank()) {
                return jwksUri;
            }
            if (issuer == null || issuer.isBlank()) {
                return null;
            }
            String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
            return base + "/oauth2/jwks";
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        public Duration getJwkCacheTtl() {
            return jwkCacheTtl;
        }

        public void setJwkCacheTtl(Duration jwkCacheTtl) {
            this.jwkCacheTtl = jwkCacheTtl;
        }
    }
}
