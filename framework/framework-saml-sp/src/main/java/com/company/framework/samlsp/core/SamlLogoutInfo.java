package com.company.framework.samlsp.core;

import java.util.List;

/**
 * IdP-initiated SLO(Single Logout)에서 외부 IdP 가 보낸 {@code <LogoutRequest>} 의 신원 정보를 정규화한 모델.
 * 로그인의 {@link SamlUserInfo} 와 대칭이며, 앱이 구현하는 {@link SamlLogoutUserResolver} 의 입력이 된다.
 *
 * @param registrationId 사용한 RelyingParty 등록 id(= IdP 식별, 로그인 시 {@link SamlUserInfo#registrationId()} 와 동일 계열)
 * @param nameId LogoutRequest 의 Subject NameID(로그인 시 받은 NameID 와 동일 — 우리 사용자 역매핑의 키)
 * @param sessionIndexes LogoutRequest 의 SessionIndex 목록(IdP 세션 식별, 없을 수 있음). 부분 로그아웃 판단 등에 쓸 수 있다.
 */
public record SamlLogoutInfo(String registrationId, String nameId, List<String> sessionIndexes) {

    public SamlLogoutInfo {
        sessionIndexes = sessionIndexes == null ? List.of() : List.copyOf(sessionIndexes);
    }

    /** SessionIndex 없는 단축 생성자. */
    public SamlLogoutInfo(String registrationId, String nameId) {
        this(registrationId, nameId, List.of());
    }
}
