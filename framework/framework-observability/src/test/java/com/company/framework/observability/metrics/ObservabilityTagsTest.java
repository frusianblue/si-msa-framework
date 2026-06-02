package com.company.framework.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObservabilityTagsTest {

    @Test
    @DisplayName("표준 3태그를 채우고 입력 순서를 보존한다")
    void fillsStandardTags() {
        Map<String, String> tags = ObservabilityTags.commonTags("user-service", "prod", "1.2.3", null);
        assertThat(tags)
                .containsExactly(
                        Map.entry("service", "user-service"), Map.entry("env", "prod"), Map.entry("version", "1.2.3"));
    }

    @Test
    @DisplayName("공백/널 값은 unknown 으로 대체하고 trim 한다")
    void blankBecomesUnknown() {
        Map<String, String> tags = ObservabilityTags.commonTags("  ", null, "  v9  ", Map.of());
        assertThat(tags)
                .containsEntry("service", "unknown")
                .containsEntry("env", "unknown")
                .containsEntry("version", "v9");
    }

    @Test
    @DisplayName("추가 태그는 뒤에 붙고 순서를 보존한다")
    void appendsExtraTags() {
        LinkedHashMap<String, String> extra = new LinkedHashMap<>();
        extra.put("region", "kr");
        extra.put("tier", "biz");
        Map<String, String> tags = ObservabilityTags.commonTags("svc", "dev", "1", extra);
        assertThat(tags)
                .containsExactly(
                        Map.entry("service", "svc"),
                        Map.entry("env", "dev"),
                        Map.entry("version", "1"),
                        Map.entry("region", "kr"),
                        Map.entry("tier", "biz"));
    }

    @Test
    @DisplayName("추가 태그가 표준 키를 override 한다")
    void extraOverridesStandard() {
        Map<String, String> tags = ObservabilityTags.commonTags("svc", "dev", "1", Map.of("env", "staging"));
        assertThat(tags).containsEntry("env", "staging");
    }

    @Test
    @DisplayName("추가 태그의 공백 키/널 값 항목은 무시한다")
    void skipsBadExtraEntries() {
        LinkedHashMap<String, String> extra = new LinkedHashMap<>();
        extra.put("  ", "x");
        extra.put("ok", null);
        extra.put(" zone ", "z");
        Map<String, String> tags = ObservabilityTags.commonTags("svc", "dev", "1", extra);
        assertThat(tags).containsEntry("zone", "z").doesNotContainKey("ok").hasSize(4);
    }
}
