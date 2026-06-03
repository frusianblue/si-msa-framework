package com.company.framework.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestContext 불변 값 객체")
class RequestContextTest {

    @Test
    @DisplayName("빌더로 필드를 설정하고 게터로 읽는다")
    void buildsAndReads() {
        RequestContext ctx = RequestContext.builder()
                .tenantId("acme")
                .userId("u-1")
                .locale(Locale.KOREA)
                .attribute("channelId", "web")
                .build();

        assertThat(ctx.tenantId()).isEqualTo("acme");
        assertThat(ctx.userId()).isEqualTo("u-1");
        assertThat(ctx.locale()).isEqualTo(Locale.KOREA);
        assertThat(ctx.attribute("channelId")).isEqualTo("web");
        assertThat(ctx.hasTenant()).isTrue();
        assertThat(ctx.hasUser()).isTrue();
    }

    @Test
    @DisplayName("공백/null 식별자는 null 로 정규화된다")
    void blankBecomesNull() {
        RequestContext ctx =
                RequestContext.builder().tenantId("   ").userId(null).build();
        assertThat(ctx.tenantId()).isNull();
        assertThat(ctx.userId()).isNull();
        assertThat(ctx.hasTenant()).isFalse();
        assertThat(ctx.hasUser()).isFalse();
    }

    @Test
    @DisplayName("EMPTY 는 모든 식별자가 비어 있다")
    void emptyHasNoIdentifiers() {
        assertThat(RequestContext.EMPTY.hasTenant()).isFalse();
        assertThat(RequestContext.EMPTY.hasUser()).isFalse();
        assertThat(RequestContext.EMPTY.attributes()).isEmpty();
    }

    @Test
    @DisplayName("attributes 맵은 읽기 전용이라 외부 변경이 막힌다")
    void attributesUnmodifiable() {
        RequestContext ctx = RequestContext.builder().attribute("k", "v").build();
        assertThatThrownBy(() -> ctx.attributes().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("빌더 입력 맵을 나중에 바꿔도 컨텍스트는 영향받지 않는다(방어적 복사)")
    void defensiveCopyOfAttributes() {
        Map<String, String> src = new HashMap<>();
        src.put("k", "v");
        RequestContext ctx = RequestContext.builder().attributes(src).build();
        src.put("k", "changed");
        assertThat(ctx.attribute("k")).isEqualTo("v");
    }

    @Test
    @DisplayName("toBuilder 로 일부만 바꾼 파생 컨텍스트를 만든다")
    void deriveWithToBuilder() {
        RequestContext base =
                RequestContext.builder().tenantId("acme").userId("u-1").build();
        RequestContext derived = base.toBuilder().userId("u-2").build();

        assertThat(derived.tenantId()).isEqualTo("acme");
        assertThat(derived.userId()).isEqualTo("u-2");
        assertThat(base.userId()).isEqualTo("u-1"); // 원본 불변
    }

    @Test
    @DisplayName("equals/hashCode 는 값 기반")
    void valueEquality() {
        RequestContext a =
                RequestContext.builder().tenantId("acme").attribute("k", "v").build();
        RequestContext b =
                RequestContext.builder().tenantId("acme").attribute("k", "v").build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
