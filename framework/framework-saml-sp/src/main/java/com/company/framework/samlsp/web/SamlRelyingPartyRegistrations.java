package com.company.framework.samlsp.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.samlsp.config.SamlSpProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

/**
 * {@link SamlSpProperties} 의 {@code registrations.<id>} 맵을 Spring Security 의
 * {@link RelyingPartyRegistrationRepository} 로 변환한다.
 *
 * <p><b>1차 경로 = IdP 메타데이터 URL.</b> {@link RelyingPartyRegistrations#fromMetadataLocation(String)} 은
 * asserting-party(IdP) 의 SSO 엔드포인트·바인딩·서명 인증서를 메타데이터에서 직접 읽어 채운다. 따라서 SS 버전마다
 * 이름이 바뀌어 온 수동 asserting-party 빌더(assertingPartyDetails ↔ assertingPartyMetadata)에 의존하지 않는다 —
 * 공공 통합인증/Keycloak/Azure AD 등 실무 IdP 는 모두 메타데이터를 제공하므로 이 경로로 충분하다.
 *
 * <p>메타데이터가 없는 IdP(엔드포인트 수동 입력)는 의도적으로 미지원(다음 단계). metadata-uri 누락 시 명확히 실패시킨다.
 */
public final class SamlRelyingPartyRegistrations {

    private SamlRelyingPartyRegistrations() {}

    public static RelyingPartyRegistrationRepository from(SamlSpProperties properties) {
        if (properties.getRegistrations().isEmpty()) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR, "SAML SP 가 활성화됐으나 framework.saml-sp.registrations 가 비어 있습니다.");
        }
        List<RelyingPartyRegistration> list = new ArrayList<>();
        properties.getRegistrations().forEach((registrationId, reg) -> list.add(build(registrationId, reg)));
        return new InMemoryRelyingPartyRegistrationRepository(list);
    }

    private static RelyingPartyRegistration build(String registrationId, SamlSpProperties.Registration reg) {
        if (reg.getMetadataUri() == null || reg.getMetadataUri().isBlank()) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "SAML 등록 '" + registrationId + "' 에 metadata-uri 가 없습니다(현재 메타데이터 기반 등록만 지원).");
        }
        RelyingPartyRegistration.Builder builder = RelyingPartyRegistrations.fromMetadataLocation(reg.getMetadataUri())
                .registrationId(registrationId);
        if (reg.getEntityId() != null && !reg.getEntityId().isBlank()) {
            builder.entityId(reg.getEntityId());
        }
        if (reg.getAssertionConsumerServiceLocation() != null
                && !reg.getAssertionConsumerServiceLocation().isBlank()) {
            builder.assertionConsumerServiceLocation(reg.getAssertionConsumerServiceLocation());
        }
        return builder.build();
    }
}
