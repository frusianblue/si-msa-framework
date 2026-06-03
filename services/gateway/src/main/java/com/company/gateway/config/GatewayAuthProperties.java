package com.company.gateway.config;

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
    private List<String> permitAllPatterns =
            new ArrayList<>(List.of("/api/*/auth/**", "/actuator/**", "/fallback/**"));

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
}
