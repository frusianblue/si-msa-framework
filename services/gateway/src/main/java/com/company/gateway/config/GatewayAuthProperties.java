package com.company.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게이트웨이 엣지 인증 설정.
 *
 * <pre>
 * gateway:
 *   auth:
 *     enabled: false                 # 기본 off(점진 도입). 운영에서 true.
 *     jwt-secret: "${JWT_SECRET}"     # framework.security.jwt.secret 와 "동일한" 비밀키여야 한다.
 *     token-type: access             # JWT typ 클레임 검증값(access 토큰만 통과)
 *     blacklist-check:                # 중앙 로그아웃(SSO) — 기본 off
 *       enabled: false               #   true 면 jti 를 reactive Redis 블랙리스트와 대조(자체 JWT 한정)
 *     authorization-server:           # 이중 발급기 — AS(OP) 토큰 수용. 기본 off(자체 JWT 만 검증)
 *       enabled: false               #   true 면 iss=AS issuer 인 RS256 토큰을 JWKS 로 검증
 *       issuer: https://auth.example.com          # AS issuer(= auth-server.issuer 와 동일)
 *       jwks-uri: https://auth.example.com/oauth2/jwks  # 생략 시 {issuer}/oauth2/jwks 로 자동 구성
 *       roles-claim: roles           # 권한 클레임명(AS RoleClaimTokenCustomizer 와 정합)
 *     user-id-header: X-User-Id       # 검증 성공 시 sub 를 이 헤더로 다운스트림에 주입
 *     roles-header: X-User-Roles      # roles 를 쉼표로 연결해 주입
 *     tenant-header: X-Tenant-Id      # 클라이언트 위조 방지를 위해 항상 제거(주입은 하지 않음 — 토큰에 없음)
 *     permit-all-patterns:            # 토큰 없이 통과(Ant 패턴)
 *       - /api/*&#47;auth/**
 *       - /actuator/**
 *       - /fallback/**
 * </pre>
 *
 * <p>secret 공유: 게이트웨이는 framework-security 에 의존하지 않으므로(서블릿/WebFlux 충돌 회피) JwtProperties 를
 * 직접 쓰지 못한다. 대신 <b>같은 비밀키</b>를 환경변수(예: {@code JWT_SECRET})로 양쪽에 주입한다.
 */
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    private boolean enabled = false;
    private String jwtSecret;
    private String tokenType = "access";
    private String userIdHeader = "X-User-Id";
    private String rolesHeader = "X-User-Roles";
    private String tenantHeader = "X-Tenant-Id";
    private List<String> permitAllPatterns = new ArrayList<>(List.of("/api/*/auth/**", "/actuator/**", "/fallback/**"));
    private final BlacklistCheck blacklistCheck = new BlacklistCheck();
    private final AuthorizationServer authorizationServer = new AuthorizationServer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
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

    public String getTenantHeader() {
        return tenantHeader;
    }

    public void setTenantHeader(String tenantHeader) {
        this.tenantHeader = tenantHeader;
    }

    public List<String> getPermitAllPatterns() {
        return permitAllPatterns;
    }

    public void setPermitAllPatterns(List<String> permitAllPatterns) {
        this.permitAllPatterns = permitAllPatterns;
    }

    public BlacklistCheck getBlacklistCheck() {
        return blacklistCheck;
    }

    public AuthorizationServer getAuthorizationServer() {
        return authorizationServer;
    }

    /**
     * 중앙 로그아웃(SSO) 옵트인 설정. {@code enabled=true} 면 게이트웨이가 access jti 를 공유 저장소
     * (reactive Redis)의 블랙리스트와 대조해 로그아웃된 토큰을 엣지에서 차단한다. 기본 off(서명/만료만 검증).
     *
     * <pre>
     * gateway:
     *   auth:
     *     blacklist-check:
     *       enabled: false   # 운영 SSO 에서 true. true 면 reactive Redis 필요(없으면 fail-fast).
     * </pre>
     */
    public static class BlacklistCheck {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 이중 발급기(AUTH_SERVER.md §4) — Authorization Server(OP)가 발급한 RS256 토큰을 엣지에서 수용하는 옵트인 설정.
     * {@code enabled=true} 면 토큰의 {@code iss} 가 {@link #issuer} 와 같을 때 {@link #jwksUri} 의 공개키로 검증한다.
     * 기본 off — 켜기 전에는 자체 JWT(HMAC)만 검증(도입 전 동작과 동일).
     *
     * <pre>
     * gateway:
     *   auth:
     *     authorization-server:
     *       enabled: false                                   # 운영 그룹사 연동 시 true
     *       issuer: ${AUTH_SERVER_ISSUER:}                   # = services/auth-server 의 auth-server.issuer
     *       jwks-uri: ${AUTH_SERVER_JWKS_URI:}               # 생략 시 {issuer}/oauth2/jwks 로 자동
     *       roles-claim: roles                               # AS RoleClaimTokenCustomizer 와 정합
     *       clock-skew: 60s
     *       jwk-cache-ttl: 1h
     * </pre>
     */
    public static class AuthorizationServer {
        private boolean enabled = false;
        private String issuer;
        private String jwksUri;
        private String rolesClaim = "roles";
        private Duration clockSkew = Duration.ofSeconds(60);
        private Duration jwkCacheTtl = Duration.ofHours(1);
        /**
         * 허용 audience 목록(비우면 aud 검증 생략 — 하위호환 기본). 설정하면 토큰의 {@code aud} 중
         * 최소 하나가 이 목록에 있어야 통과(혼동된 대리(confused deputy) 방지 — 다른 RP/리소스용 토큰 차단).
         * 보통 게이트웨이 뒤 리소스 식별자(또는 AS 의 audience 값)를 넣는다.
         */
        private java.util.List<String> audiences = new java.util.ArrayList<>();

        /** jwks-uri 가 비어 있으면 issuer 로부터 표준 경로({issuer}/oauth2/jwks)를 구성한다. */
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

        public java.util.List<String> getAudiences() {
            return audiences;
        }

        public void setAudiences(java.util.List<String> audiences) {
            this.audiences = (audiences == null) ? new java.util.ArrayList<>() : audiences;
        }
    }
}
