package com.company.framework.webauthn.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 패스키/WebAuthn 설정. 모듈은 기본 비활성(framework.webauthn.enabled=false).
 *
 * <pre>
 * framework:
 *   webauthn:
 *     enabled: false
 *     rp-id: "example.com"                       # Relying Party ID = 등록 가능 도메인(서브도메인 공유). 로컬은 "localhost".
 *     rp-name: "SI-MSA"                           # 인증기 UI 표시명(미설정 시 rp-id 사용).
 *     allowed-origins:                            # ceremony 를 허용할 공개 origin(게이트웨이 Ingress 호스트/TLS).
 *       - "https://app.example.com"
 *     token-path: "/api/v1/auth/webauthn/token"   # 패스키 인증 성공(세션) → 프레임워크 JWT 교환 엔드포인트.
 *     credentials-path: "/api/v1/auth/webauthn/credentials" # 패스키 목록 조회/삭제(관리) 베이스 경로.
 *     store:
 *       type: memory                              # memory(개발) | jdbc(영속 — 재기동 후 자격증명 유지)
 * </pre>
 *
 * <p><b>제약</b>: WebAuthn 은 SecureContext(HTTPS)에서만 동작한다(localhost 예외). dev/prod 는 Ingress TLS 전제.
 * rpId/origin 불일치는 ceremony 거부 사유 — 멀티서비스에서 rpId/origin 정합을 일원화한다.
 */
@ConfigurationProperties(prefix = "framework.webauthn")
public class WebAuthnProperties {

    /** 모듈 전체 on/off. */
    private boolean enabled = false;

    /** Relying Party ID(등록 가능 도메인). 예: {@code example.com}, 로컬은 {@code localhost}. */
    private String rpId = "localhost";

    /** 인증기 UI 표시명. 비우면 {@link #rpId} 가 사용된다. */
    private String rpName = "SI-MSA";

    /** ceremony 를 허용할 공개 origin 목록(스킴+호스트+포트). 비우면 DSL 기본값에 위임. */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 패스키 인증 성공(세션) 후 프레임워크 표준 JWT 로 교환하는 엔드포인트 경로. */
    private String tokenPath = "/api/v1/auth/webauthn/token";

    /** 패스키 자격증명 관리(목록 조회/삭제) 엔드포인트의 베이스 경로. 삭제는 {@code {credentialsPath}/{credentialId}}. */
    private String credentialsPath = "/api/v1/auth/webauthn/credentials";

    @NestedConfigurationProperty
    private Store store = new Store();

    public static class Store {
        /** 자격증명 영속 백엔드. memory(개발) | jdbc(운영). */
        private String type = "memory";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    /** rpName 이 비어 있으면 rpId 로 폴백한 표시명. */
    public String resolvedRpName() {
        return (rpName == null || rpName.isBlank()) ? rpId : rpName;
    }
}
