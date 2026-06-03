package com.company.framework.samlsp.core;

import java.util.List;
import java.util.Map;

/**
 * SAML 2.0 Assertion 의 NameID + Attribute 를 정규화한 표준 신원 모델. 소셜 로그인의
 * {@code OAuthUserInfo} 와 대칭이며, 앱이 구현하는 {@link SamlUserResolver} 의 입력이 된다.
 *
 * @param registrationId 사용한 RelyingParty 등록 id(= IdP 식별, OAuth provider id 에 대응)
 * @param nameId Assertion 의 Subject NameID(우리 시스템 사용자 매핑의 키, OAuth providerId 에 대응)
 * @param email 이메일(없을 수 있음 — IdP 가 해당 속성을 내려주지 않으면 null)
 * @param name 표시 이름(없을 수 있음)
 * @param attributes Attribute 원본(키 → 값 목록). SAML 속성은 다중값이라 {@code List<Object>} 로 보존
 */
public record SamlUserInfo(
        String registrationId, String nameId, String email, String name, Map<String, List<Object>> attributes) {}
