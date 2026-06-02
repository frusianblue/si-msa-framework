package com.company.framework.observability.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 공통 메트릭 태그 해석 — 순수 로직(micrometer 비의존, 컨텍스트 무의존).
 *
 * <p>표준 키 3종을 항상 채운다: {@code service} / {@code env} / {@code version}.
 * 값이 비었으면 {@code "unknown"} 으로 대체하고, 추가 태그({@code extraTags})는 뒤에 붙이되
 * 표준 키와 충돌하면 추가 태그가 우선(override)한다. 입력 순서를 보존한다(LinkedHashMap).
 *
 * <p>k8s 라벨/OTel 시맨틱 컨벤션과 정렬하기 위해 키 이름을 단순 소문자로 고정한다.
 * micrometer {@code Tag} 변환은 오토컨피그( {@code ObservabilityAutoConfiguration} )에서 수행한다 —
 * 여기는 순수 JDK 라 단위테스트/오프라인 실행검증이 가능하다.
 */
public final class ObservabilityTags {

    public static final String SERVICE = "service";
    public static final String ENV = "env";
    public static final String VERSION = "version";

    private ObservabilityTags() {}

    /**
     * 표준 태그 맵을 만든다.
     *
     * @param service 서비스명(보통 spring.application.name) — 비면 "unknown"
     * @param env 환경/프로파일(prod/stg/dev …) — 비면 "unknown"
     * @param version 빌드 버전 — 비면 "unknown"
     * @param extra 추가 태그(널 허용). 빈 키/널 값 항목은 무시. 표준 키와 같으면 override.
     * @return 순서가 보존된 태그 맵(key→value)
     */
    public static LinkedHashMap<String, String> commonTags(
            String service, String env, String version, Map<String, String> extra) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put(SERVICE, blankToUnknown(service));
        tags.put(ENV, blankToUnknown(env));
        tags.put(VERSION, blankToUnknown(version));
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                    tags.put(e.getKey().trim(), e.getValue());
                }
            }
        }
        return tags;
    }

    private static String blankToUnknown(String v) {
        return (v == null || v.isBlank()) ? "unknown" : v.trim();
    }
}
