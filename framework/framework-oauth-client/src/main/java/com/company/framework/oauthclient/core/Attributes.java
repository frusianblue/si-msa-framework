package com.company.framework.oauthclient.core;

import java.util.Map;

/**
 * userinfo 응답(JSON → Map)에서 점(.) 경로로 값을 꺼내는 헬퍼. 카카오({@code kakao_account.email})·
 * 네이버({@code response.id}) 처럼 중첩 응답을 평탄 경로로 접근한다. 외부 의존성 없음(JDK 단독).
 */
final class Attributes {

    private Attributes() {}

    /** 점 경로(예: "kakao_account.profile.nickname")로 문자열 값을 best-effort 추출. 없으면 null. */
    @SuppressWarnings("unchecked")
    static String getString(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) return null;
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = ((Map<String, Object>) map).get(segment);
            if (current == null) return null;
        }
        return String.valueOf(current);
    }
}
