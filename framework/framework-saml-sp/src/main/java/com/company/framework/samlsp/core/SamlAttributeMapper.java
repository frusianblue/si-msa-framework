package com.company.framework.samlsp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SAML Assertion 의 NameID + Attribute(Map) 를 {@link SamlUserInfo} 로 정규화하는 순수 로직(JDK 단독, SAML/Spring 무의존).
 *
 * <p>SAML 속성 키는 IdP 마다 제각각이다 — friendly name({@code email}, {@code mail}, {@code displayName})일 수도,
 * URN/OID({@code urn:oid:1.2.840.113549.1.9.1} = email, {@code urn:oid:2.16.840.1.113730.3.1.241} = displayName)일 수도 있다.
 * 그래서 후보 키 목록을 순서대로 시도해 처음 발견된 비어있지 않은 값을 채택한다(설정 키 우선 → 표준 기본 후보).
 *
 * <p>OpenSAML/Spring Security 타입에 의존하지 않도록, 입력은 이미 추출된 {@code nameId} 와
 * {@code Map<String, List<Object>>}(= Spring Security {@code Saml2AuthenticatedPrincipal.getAttributes()} 형태) 만 받는다.
 * 따라서 이 클래스는 네트워크/IdP 없이 단위 검증된다(소셜 로그인의 {@code Attributes} 헬퍼와 같은 결).
 */
public final class SamlAttributeMapper {

    /** 이메일 속성의 표준 후보(friendly + OID). 설정 후보가 먼저 시도된 뒤 보충된다. */
    private static final List<String> DEFAULT_EMAIL_KEYS = List.of("email", "mail", "urn:oid:1.2.840.113549.1.9.1");

    /** 표시 이름 속성의 표준 후보. */
    private static final List<String> DEFAULT_NAME_KEYS =
            List.of("displayName", "name", "cn", "urn:oid:2.16.840.1.113730.3.1.241", "urn:oid:2.5.4.3");

    private final List<String> emailKeys;
    private final List<String> nameKeys;

    /**
     * @param emailAttribute 설정에서 지정한 이메일 속성 키(없으면 null/blank — 기본 후보만 사용)
     * @param nameAttribute 설정에서 지정한 표시 이름 속성 키(없으면 null/blank — 기본 후보만 사용)
     */
    public SamlAttributeMapper(String emailAttribute, String nameAttribute) {
        this.emailKeys = prepend(emailAttribute, DEFAULT_EMAIL_KEYS);
        this.nameKeys = prepend(nameAttribute, DEFAULT_NAME_KEYS);
    }

    /** 추출된 NameID + 속성 맵을 정규화 신원으로 만든다. attributes 가 null 이면 빈 맵으로 취급. */
    public SamlUserInfo map(String registrationId, String nameId, Map<String, List<Object>> attributes) {
        Map<String, List<Object>> attrs = (attributes == null) ? Map.of() : attributes;
        String email = firstString(attrs, emailKeys);
        String name = firstString(attrs, nameKeys);
        return new SamlUserInfo(registrationId, nameId, email, name, attrs);
    }

    /** 후보 키들을 순서대로 보며 처음 발견되는 비어있지 않은 문자열 값을 반환(첫 원소 기준). 없으면 null. */
    private static String firstString(Map<String, List<Object>> attrs, List<String> keys) {
        for (String key : keys) {
            List<Object> values = attrs.get(key);
            if (values == null || values.isEmpty()) {
                continue;
            }
            Object first = values.get(0);
            if (first == null) {
                continue;
            }
            String text = String.valueOf(first).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    /** 설정 키(비어있지 않으면)를 기본 후보 앞에 끼워 중복 없는 순서 목록을 만든다. */
    private static List<String> prepend(String configured, List<String> defaults) {
        Map<String, Boolean> ordered = new LinkedHashMap<>();
        if (configured != null && !configured.isBlank()) {
            ordered.put(configured.trim(), Boolean.TRUE);
        }
        for (String key : defaults) {
            ordered.put(key, Boolean.TRUE);
        }
        return List.copyOf(ordered.keySet());
    }
}
