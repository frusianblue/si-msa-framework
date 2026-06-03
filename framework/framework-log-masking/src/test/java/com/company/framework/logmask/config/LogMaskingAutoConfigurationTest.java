package com.company.framework.logmask.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.logmask.logback.MaskingSupport;
import com.company.framework.logmask.mask.SensitiveDataMasker;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 로그 마스킹 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.log-masking.enabled} 미지정 → 빈 미등록(선택형 기본 off).
 *   <li>enabled=true → {@link SensitiveDataMasker} + {@link LogMaskingInstaller} 등록, 정적 다리에 설치됨.
 *   <li>install-converter=false → 설치기 미생성(빈 직접 호출 경로만).
 *   <li>프로퍼티로 규칙/커스텀 패턴이 반영된다.
 * </ul>
 */
class LogMaskingAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LogMaskingAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        // 설치기 destroy 가 컨텍스트 종료 시 clear 하지만, 방어적으로 해제.
        MaskingSupport.clear();
    }

    @Test
    @DisplayName("enabled 미지정 → 빈 미등록(기본 off)")
    void backsOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SensitiveDataMasker.class);
            assertThat(context).doesNotHaveBean(LogMaskingInstaller.class);
        });
    }

    @Test
    @DisplayName("enabled=true → 마스커+설치기 등록, 정적 다리에 설치")
    void registersBeansWhenEnabled() {
        runner.withPropertyValues("framework.log-masking.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SensitiveDataMasker.class);
            assertThat(context).hasSingleBean(LogMaskingInstaller.class);
            assertThat(MaskingSupport.isInstalled()).isTrue();
            assertThat(context.getBean(SensitiveDataMasker.class).ruleNames())
                    .containsExactly("card", "rrn", "phone", "email");
        });
    }

    @Test
    @DisplayName("install-converter=false → 설치기 미생성(마스커 빈만)")
    void skipsInstallerWhenConverterDisabled() {
        runner.withPropertyValues("framework.log-masking.enabled=true", "framework.log-masking.install-converter=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SensitiveDataMasker.class);
                    assertThat(context).doesNotHaveBean(LogMaskingInstaller.class);
                });
    }

    @Test
    @DisplayName("프로퍼티로 규칙 토글과 커스텀 패턴이 반영된다")
    void appliesRuleTogglesAndCustomPatterns() {
        runner.withPropertyValues(
                        "framework.log-masking.enabled=true",
                        "framework.log-masking.rules.phone=false",
                        "framework.log-masking.rules.account=true",
                        "framework.log-masking.custom-patterns.emp=EMP\\d{6}")
                .run(context -> {
                    SensitiveDataMasker masker = context.getBean(SensitiveDataMasker.class);
                    assertThat(masker.ruleNames()).containsExactly("card", "rrn", "email", "account", "emp");
                    assertThat(masker.mask("사번 EMP123456")).isEqualTo("사번 *********");
                    // phone off → 휴대폰 규칙 부재. 단 account=true 라 '010-1234-5678' 같은 구분자 있는 형태는
                    // 계좌 패턴(2~6-2~6-2~6)에 걸리므로, 휴대폰 규칙만 잡는 '구분자 없는' 번호로 검증한다.
                    assertThat(masker.mask("phone 01012345678")).isEqualTo("phone 01012345678");
                });
    }

    /**
     * 레지스트레이션 가드: 클래스패스의 모든 {@code AutoConfiguration.imports} 를 직접 읽어 자동활성 경로가 실제로
     * 등록돼 있음을 보장(과거 .imports 누락으로 모듈이 조용히 비활성됐던 교훈).
     */
    @Test
    @DisplayName("LogMaskingAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
    void autoConfigurationIsRegisteredInImports() throws Exception {
        String path = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        List<String> registered = new ArrayList<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(path);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(registered::add);
            }
        }
        assertThat(registered)
                .as(".imports 에 LogMaskingAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(LogMaskingAutoConfiguration.class.getName());
    }
}
