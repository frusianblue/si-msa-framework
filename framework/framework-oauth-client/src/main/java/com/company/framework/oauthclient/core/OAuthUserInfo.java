package com.company.framework.oauthclient.core;

import java.util.Map;

/**
 * 외부 IdP(구글/카카오 등) userinfo 응답을 정규화한 표준 모델.
 *
 * @param provider 공급자 id(google/kakao/naver/...)
 * @param providerId 공급자 내 고유 식별자(sub/id 등). 우리 시스템 사용자 매핑의 키.
 * @param email 이메일(없을 수 있음 — 동의항목 미포함 시 null)
 * @param name 표시 이름(없을 수 있음)
 * @param attributes userinfo 원본 전체(앱이 추가 필드를 활용할 수 있도록 보존)
 */
public record OAuthUserInfo(
        String provider, String providerId, String email, String name, Map<String, Object> attributes) {}
