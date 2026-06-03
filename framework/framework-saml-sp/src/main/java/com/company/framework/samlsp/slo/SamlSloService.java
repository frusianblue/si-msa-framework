package com.company.framework.samlsp.slo;

import com.company.framework.samlsp.core.SamlLogoutInfo;
import com.company.framework.samlsp.core.SamlLogoutUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IdP-initiated SAML SLO 오케스트레이션(<b>무상태</b>). 외부 IdP 가 보낸 <b>검증이 끝난</b> LogoutRequest 의 신원
 * ({@link SamlLogoutInfo})을 받아, {@link SamlLogoutUserResolver} 로 우리 사용자 id 로 역매핑한 뒤
 * {@link SamlSessionTerminator} 로 그 사용자의 모든 토큰을 무효화한다(자체 JWT 블랙리스트). NameID 가 우리 사용자로
 * 매핑되지 않으면 조용히 0 을 반환한다(graceful — 미상의 주체로 흐름을 깨지 않는다).
 *
 * <p>SAML {@code <LogoutRequest>} 의 <b>서명 검증·XML 파싱·{@code <LogoutResponse>} 생성은 이 클래스의 책임이 아니다</b>
 * (Spring Security 의 SLO 필터/리졸버가 담당). 이 클래스는 검증된 신원만 받아 토큰 무효화로 잇는다 — 그래서
 * OpenSAML 무의존이며 순수 JDK 로 단위검증된다(6.1 의 {@code Saml2AuthnRequestCodec} 과 같은 결: 검증 가능한 핵심과
 * 받는 쪽이 검증하는 SAML 본체를 분리).
 *
 * <p><b>완전 무상태 NameID 추출 주의</b>: 서버 세션 없는 순수 Bearer 배포에서 LogoutRequest 의 NameID 를 XML 에서 직접
 * 얻으려면 OpenSAML 기반 디코더가 필요하다(확장점, 문서 참조). 본 서비스는 신원이 추출/검증된 이후의 무효화 단계다.
 */
public class SamlSloService {

    private static final Logger log = LoggerFactory.getLogger(SamlSloService.class);

    private final SamlLogoutUserResolver userResolver;
    private final SamlSessionTerminator terminator;

    public SamlSloService(SamlLogoutUserResolver userResolver, SamlSessionTerminator terminator) {
        this.userResolver = userResolver;
        this.terminator = terminator;
    }

    /**
     * 검증된 LogoutRequest 신원을 받아 해당 사용자의 토큰을 무효화한다.
     *
     * @param info 검증된 신원(null/blank nameId 면 무시)
     * @return 무효화한 세션 수(매핑 실패 또는 무효화 대상 없음이면 0)
     */
    public int onLogoutRequest(SamlLogoutInfo info) {
        if (info == null || info.nameId() == null || info.nameId().isBlank()) {
            log.debug("SAML SLO: nameId 없음 → 무시");
            return 0;
        }
        String userId = userResolver.resolveUserId(info);
        if (userId == null || userId.isBlank()) {
            // 사용자 제어값(nameId) 미로깅 — registrationId 만.
            log.debug("SAML SLO: registrationId={} 의 NameID 가 우리 사용자로 매핑되지 않음 → 무시", info.registrationId());
            return 0;
        }
        int terminated = terminator.terminateAll(userId, "saml-slo");
        log.debug("SAML SLO: registrationId={} 사용자 토큰 무효화(세션 {}건)", info.registrationId(), terminated);
        return terminated;
    }
}
