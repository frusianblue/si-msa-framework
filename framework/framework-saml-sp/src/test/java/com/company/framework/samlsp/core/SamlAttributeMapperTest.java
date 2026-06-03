package com.company.framework.samlsp.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 속성 매핑 순수 로직 검증(SAML/Spring 무의존, 네트워크 없음). IdP 마다 다른 속성 키(friendly/OID),
 * 다중값, 설정 키 우선, 빈/null 안전성을 다룬다.
 */
class SamlAttributeMapperTest {

    @Test
    @DisplayName("friendly 속성 + 다중값: 첫 원소 채택, registrationId/nameId/원본 보존")
    void friendlyAttributesAndMultiValue() {
        Map<String, List<Object>> attrs = new LinkedHashMap<>();
        attrs.put("email", List.of("alice@corp.example"));
        attrs.put("displayName", List.of("Alice Kim", "ignored"));

        SamlUserInfo u = new SamlAttributeMapper(null, null).map("corp", "alice-nameid", attrs);

        assertThat(u.registrationId()).isEqualTo("corp");
        assertThat(u.nameId()).isEqualTo("alice-nameid");
        assertThat(u.email()).isEqualTo("alice@corp.example");
        assertThat(u.name()).isEqualTo("Alice Kim");
        assertThat(u.attributes().get("displayName")).hasSize(2);
    }

    @Test
    @DisplayName("OID 속성만 있는 IdP: 기본 후보(OID)로 fallback")
    void oidFallback() {
        Map<String, List<Object>> attrs = new LinkedHashMap<>();
        attrs.put("urn:oid:1.2.840.113549.1.9.1", List.of("bob@corp.example"));
        attrs.put("urn:oid:2.16.840.1.113730.3.1.241", List.of("Bob Lee"));

        SamlUserInfo u = new SamlAttributeMapper(null, null).map("corp", "bob", attrs);

        assertThat(u.email()).isEqualTo("bob@corp.example");
        assertThat(u.name()).isEqualTo("Bob Lee");
    }

    @Test
    @DisplayName("설정 속성 키가 기본 후보보다 우선")
    void configuredKeyWins() {
        Map<String, List<Object>> attrs = new LinkedHashMap<>();
        attrs.put("email", List.of("default@x"));
        attrs.put("workmail", List.of("custom@x"));

        SamlUserInfo u = new SamlAttributeMapper("workmail", null).map("corp", "c", attrs);

        assertThat(u.email()).isEqualTo("custom@x");
    }

    @Test
    @DisplayName("null/빈/공백/누락 안전: 다음 후보로 진행하거나 null 반환")
    void nullAndBlankSafe() {
        SamlUserInfo none = new SamlAttributeMapper(null, null).map("corp", "none", null);
        assertThat(none.email()).isNull();
        assertThat(none.attributes()).isEmpty();

        Map<String, List<Object>> blanks = new LinkedHashMap<>();
        blanks.put("email", List.of("   ")); // 공백만 → 스킵
        blanks.put("mail", List.of("real@x")); // 다음 후보 채택
        assertThat(new SamlAttributeMapper(null, null).map("c", "n", blanks).email())
                .isEqualTo("real@x");

        List<Object> withNull = new ArrayList<>();
        withNull.add(null);
        Map<String, List<Object>> nullVal = new LinkedHashMap<>();
        nullVal.put("name", withNull);
        assertThat(new SamlAttributeMapper(null, null).map("c", "n", nullVal).name())
                .isNull();
    }
}
