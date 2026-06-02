package com.company.framework.observability.config;

import com.company.framework.observability.metrics.ObservabilityTags;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
// ⚠️ Boot 4 패키지 재편: MeterRegistryCustomizer 는 actuate.autoconfigure.metrics 가 아니라
//    org.springframework.boot.micrometer.metrics.autoconfigure 로 이동했다(3.x→4 변경).
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 관측 모듈 자동 설정.
 *
 * <p>1단(모듈+기능): {@code framework.observability.enabled=true}.
 * 2단(메트릭 공통태그): {@code micrometer-core} 가 classpath 에 있고
 * {@code framework.observability.metrics.common-tags-enabled=true}(기본).
 * 3단(override): 앱이 동일 이름 빈을 등록하면 양보({@link ConditionalOnMissingBean}).
 *
 * <p>제공 빈: {@code frameworkObservabilityCommonTags} — 모든 {@link MeterRegistry} 에
 * {@code service}/{@code env}/{@code version}(+추가 태그) 공통 태그를 부여한다. Boot 가 레지스트리
 * 생성 시 모든 {@link MeterRegistryCustomizer} 를 모아 적용하므로 별도 순서 지정이 필요 없다.
 *
 * <p>구조화 로그·액추에이터 노출·OTLP 익스포터 등 "프로퍼티로만 켜지는" 표준값은
 * {@link ObservabilityEnvironmentPostProcessor} 가 컨텍스트/로깅 초기화 전에 풀어준다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "framework.observability", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(
            prefix = "framework.observability.metrics",
            name = "common-tags-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "frameworkObservabilityCommonTags")
    public MeterRegistryCustomizer<MeterRegistry> frameworkObservabilityCommonTags(
            ObservabilityProperties props, Environment env) {
        String service = firstNonBlank(props.getService(), env.getProperty("spring.application.name"));
        String envName = firstNonBlank(props.getEnv(), firstActiveProfile(env));
        String version = props.getVersion();

        Map<String, String> resolved =
                ObservabilityTags.commonTags(service, envName, version, props.getMetrics().getExtraTags());
        List<Tag> tags = resolved.entrySet().stream()
                .map(e -> Tag.of(e.getKey(), e.getValue()))
                .toList();

        return registry -> registry.config().commonTags(tags);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b; // null/blank 면 ObservabilityTags 가 "unknown" 으로 대체
    }

    private static String firstActiveProfile(Environment env) {
        String[] profiles = env.getActiveProfiles();
        return (profiles != null && profiles.length > 0) ? profiles[0] : null;
    }
}
