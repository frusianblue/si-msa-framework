package com.company.framework.samlsp.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SAML 2.0 SP 설정. 3단 토글의 2단(기능 on/off)과, IdP 별 RelyingParty 등록 정보를 담는다.
 *
 * <p>OAuth 의 {@code providers.<id>} 맵과 대칭으로 {@code registrations.<id>} 맵을 둔다. 1차 경로는
 * <b>IdP 메타데이터 URL</b> 기반 등록(공공 통합인증/Keycloak/Azure AD 모두 메타데이터 제공) — asserting-party
 * 엔드포인트·서명 인증서를 메타데이터가 공급하므로 버전 민감한 수동 빌더를 피한다.
 *
 * <pre>
 * framework:
 *   saml-sp:
 *     enabled: true
 *     request-repository: session   # session(단일 파드 기본) | redis(멀티 파드)
 *     registrations:
 *       corp:
 *         metadata-uri: "https://idp.example.com/realms/corp/protocol/saml/descriptor"
 *         entity-id: "{baseUrl}/saml2/service-provider-metadata/{registrationId}"
 *         email-attribute: "email"      # 선택(미지정 시 email/mail/urn:oid:.. 후보 자동 시도)
 *         name-attribute: "displayName" # 선택
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.saml-sp")
public class SamlSpProperties {

    /** 2단 기능 토글. 기본 off(모듈 컨벤션). */
    private boolean enabled = false;

    /**
     * AuthnRequest in-flight 저장소: {@code session}(기본, 단일 파드 또는 게이트웨이 스티키 세션) |
     * {@code redis}(멀티 파드 공유, 세션 불필요).
     *
     * <p><b>멀티 파드:</b> SP-initiated 흐름은 AuthnRequest↔Response 상관을 보관해야 한다. {@code session} 은 서버
     * 세션(JSESSIONID)에 묶으므로 게이트웨이가 authorize 와 ACS 콜백을 다른 파드로 보내면 깨진다(스티키 세션 필요).
     * {@code redis} 는 상관관계 쿠키 + redis 공유 저장소로 세션 없이 파드 간 동작한다(권장, 운영). redis 선택 시
     * {@link #redis} 의 쿠키 설정을 따른다 — 특히 {@code cookie-same-site=None}(POST 바인딩 ACS 크로스사이트 콜백
     * 필수) + {@code cookie-secure=true}(HTTPS) 가 기본이며, 잘못된 조합은 시작 시 fail-fast 된다.
     *
     * <p>{@code redis} 인데 {@code spring-boot-starter-data-redis}(=StringRedisTemplate) 가 클래스패스에 없으면
     * 오토컨피그가 시작 시 명확히 실패한다(조용한 session 폴백 금지).
     */
    private RequestRepository requestRepository = RequestRepository.SESSION;

    /** 성공 후 자체 JWT 발급 뒤 리다이렉트할 기본 continue URL(요청에 continue 파라미터가 없을 때). */
    private String defaultContinueUrl;

    /** IdP 별 RelyingParty 등록. 키 = registrationId. */
    private Map<String, Registration> registrations = new LinkedHashMap<>();

    /** {@code request-repository=redis} 일 때의 redis/쿠키 세부 설정. */
    private Redis redis = new Redis();

    /** IdP-initiated SLO(Single Logout) 수신 설정(6.2). */
    private Slo slo = new Slo();

    public enum RequestRepository {
        SESSION,
        REDIS
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RequestRepository getRequestRepository() {
        return requestRepository;
    }

    public void setRequestRepository(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public String getDefaultContinueUrl() {
        return defaultContinueUrl;
    }

    public void setDefaultContinueUrl(String defaultContinueUrl) {
        this.defaultContinueUrl = defaultContinueUrl;
    }

    public Map<String, Registration> getRegistrations() {
        return registrations;
    }

    public void setRegistrations(Map<String, Registration> registrations) {
        this.registrations = registrations;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Slo getSlo() {
        return slo;
    }

    public void setSlo(Slo slo) {
        this.slo = slo;
    }

    /**
     * {@code request-repository=redis} 전용 설정. AuthnRequest 직렬값을 보관할 redis 키 접두/TTL 과, save↔load 상관을
     * 잇는 상관관계 쿠키의 속성을 담는다. 기본값은 운영(HTTPS/k8s)을 가정한다.
     */
    public static class Redis {

        /** redis 키 접두사. 최종 키 = {@code keyPrefix + 1회용UUID}. */
        private String keyPrefix = "saml:authnreq:";

        /** AuthnRequest 보관 TTL(핸드셰이크는 수초이나 IdP MFA 등 여유). redis 네이티브 만료. */
        private Duration ttl = Duration.ofMinutes(5);

        /** 상관관계 쿠키 이름. */
        private String cookieName = "SAML_AUTHN_KEY";

        /**
         * 쿠키 SameSite. {@code None}(기본) | {@code Lax} | {@code Strict}. <b>POST 바인딩 ACS 콜백은 크로스사이트
         * top-level POST 이므로 {@code None} 이 아니면 쿠키가 콜백에 실리지 않아 인증이 깨진다.</b> REDIRECT 바인딩만
         * 쓰는 환경이면 {@code Lax} 도 가능. {@code None} 은 {@code cookieSecure=true} 를 요구한다(브라우저 규칙).
         */
        private String cookieSameSite = "None";

        /** 쿠키 Secure 플래그. {@code SameSite=None} 이면 필수(HTTPS). 로컬 평문 HTTP 개발은 session 저장소 권장. */
        private boolean cookieSecure = true;

        /** 쿠키 Path. SAML 엔드포인트가 컨텍스트 루트면 {@code /} 로 충분. */
        private String cookiePath = "/";

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookiePath() {
            return cookiePath;
        }

        public void setCookiePath(String cookiePath) {
            this.cookiePath = cookiePath;
        }
    }

    /**
     * IdP-initiated SLO(Single Logout) 수신 설정(6.2). 외부 IdP 가 중앙 로그아웃 시 우리 SLO 엔드포인트로
     * {@code <LogoutRequest>} 를 보내면, NameID 를 우리 사용자로 역매핑({@code SamlLogoutUserResolver})해
     * 그 사용자의 자체 JWT 를 전부 무효화한다.
     *
     * <p>기본 비활성. 활성화하려면 {@code enabled=true} + {@code SamlLogoutUserResolver} 빈 등록이 필요하다
     * (그래야 NameID→우리 사용자 매핑이 가능). 토큰 무효화 완전 커버는 {@code framework.security.concurrent-session.enabled=true}
     * + 공유 TokenStore(redis) 가 전제다(레지스트리로 사용자별 세션을 열거해야 하므로).
     */
    public static class Slo {

        /** IdP-initiated SLO 수신 활성화(기본 false). */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** 단일 IdP(RelyingParty) 등록 정보. */
    public static class Registration {

        /** IdP 메타데이터 URL(권장 경로). 지정 시 asserting-party 엔드포인트/서명 인증서를 여기서 가져온다. */
        private String metadataUri;

        /** SP entityId. {baseUrl}/{registrationId} 플레이스홀더 사용 가능. 미지정 시 SS 기본 템플릿. */
        private String entityId;

        /** ACS(Assertion Consumer Service) 위치. 미지정 시 SS 기본 {baseUrl}/login/saml2/sso/{registrationId}. */
        private String assertionConsumerServiceLocation;

        /** 이메일 속성 키(선택). 미지정 시 email/mail/urn:oid:1.2.840.113549.1.9.1 후보를 순서대로 시도. */
        private String emailAttribute;

        /** 표시 이름 속성 키(선택). 미지정 시 displayName/name/cn/urn:oid:.. 후보를 순서대로 시도. */
        private String nameAttribute;

        public String getMetadataUri() {
            return metadataUri;
        }

        public void setMetadataUri(String metadataUri) {
            this.metadataUri = metadataUri;
        }

        public String getEntityId() {
            return entityId;
        }

        public void setEntityId(String entityId) {
            this.entityId = entityId;
        }

        public String getAssertionConsumerServiceLocation() {
            return assertionConsumerServiceLocation;
        }

        public void setAssertionConsumerServiceLocation(String assertionConsumerServiceLocation) {
            this.assertionConsumerServiceLocation = assertionConsumerServiceLocation;
        }

        public String getEmailAttribute() {
            return emailAttribute;
        }

        public void setEmailAttribute(String emailAttribute) {
            this.emailAttribute = emailAttribute;
        }

        public String getNameAttribute() {
            return nameAttribute;
        }

        public void setNameAttribute(String nameAttribute) {
            this.nameAttribute = nameAttribute;
        }
    }
}
