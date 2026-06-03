package com.company.framework.samlsp.core;

/**
 * IdP-initiated SAML SLO 에서 프로젝트가 구현하는 <b>역매핑</b> 계약. 로그인의 {@link SamlUserResolver} 와 대칭으로,
 * 외부 IdP 가 보낸 NameID({@link SamlLogoutInfo})를 우리 시스템 사용자 id 로 되돌린다(로그인 시 nameId→사용자 매핑의 역).
 * 반환된 사용자 id 로 그 사용자의 자체 JWT(access/refresh)를 전부 무효화한다.
 *
 * <p>이 빈을 등록해야 IdP-initiated SLO 수신이 활성화된다(미등록이면 NameID 를 우리 사용자로 매핑할 길이 없어 SLO 수신 비활성).
 * 보통 {@link SamlUserResolver} 가 로그인 시 nameId↔userId 를 저장해 두므로, 그 저장소를 역조회하면 된다.
 *
 * @implNote 매칭되는 사용자가 없으면 {@code null} 을 반환한다(graceful no-op — 알 수 없는 NameID 로는 아무것도 무효화하지 않는다).
 *     예외를 던지지 말 것: IdP 가 보낸 LogoutRequest 처리는 관용적이어야 하며, 미상의 주체로 인해 흐름이 깨지면 안 된다.
 */
public interface SamlLogoutUserResolver {

    /**
     * @param info 검증된 LogoutRequest 의 신원(registrationId + NameID + SessionIndex)
     * @return 무효화 대상 우리 사용자 id, 매핑 실패 시 {@code null}
     */
    String resolveUserId(SamlLogoutInfo info);
}
