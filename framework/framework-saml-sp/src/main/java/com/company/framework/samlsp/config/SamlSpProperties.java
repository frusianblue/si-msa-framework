package com.company.framework.samlsp.config;

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
     * {@code redis}(멀티 파드 공유 — <b>예정, 현재 미구현</b>).
     *
     * <p><b>멀티 파드 주의:</b> SP-initiated 흐름은 AuthnRequest↔Response 상관을 HTTP 세션에 보관한다. 게이트웨이가
     * authorize 와 ACS 콜백을 서로 다른 파드로 보내면 세션이 없어 인증이 깨진다. 현재 권장 해법은 <b>SAML 핸드셰이크
     * 구간(수초)에 한해 게이트웨이/인그레스 스티키 세션</b>. redis 공유 저장소({@code Saml2AuthenticationRequestRepository}
     * redis 구현)는 다음 단계이며, 그때까지 {@code redis} 로 설정하면 오토컨피그가 시작 시 명확히 실패시킨다(조용한 no-op 방지).
     */
    private RequestRepository requestRepository = RequestRepository.SESSION;

    /** 성공 후 자체 JWT 발급 뒤 리다이렉트할 기본 continue URL(요청에 continue 파라미터가 없을 때). */
    private String defaultContinueUrl;

    /** IdP 별 RelyingParty 등록. 키 = registrationId. */
    private Map<String, Registration> registrations = new LinkedHashMap<>();

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
