package com.company.framework.commoncode.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.commoncode.service.CommonCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 공통코드 오토컨피그 토글(백오프) 스모크.
 *
 * <p>{@code CommonCodeAutoConfiguration} 은 클래스레벨 {@code @MapperScan} + {@code matchIfMissing=true}(기본 ON)
 * 이라, 활성 경로는 MyBatis {@code SqlSessionFactory}/DataSource 를 요구한다 — 순수 로딩 스모크 범위를 벗어난다.
 * 따라서 본 테스트는 {@code framework.commoncode.enabled=false} 비활성 경로를 검증한다: 설정 클래스가 제외되면
 * {@code @MapperScan} 도 처리되지 않아 컨텍스트가 깨지지 않고 어떤 빈도 만들지 않음(완전 하위호환).
 *
 * <p>(활성 경로의 풀 와이어링은 MyBatis 인프라가 있는 서비스 슬라이스 테스트에서 다룬다.)
 */
class CommonCodeAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CommonCodeAutoConfiguration.class));

    @Test
    @DisplayName("enabled=false → MapperScan 미처리·컨텍스트 정상·빈 없음")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("framework.commoncode.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CommonCodeService.class);
        });
    }
}
