package com.company.framework.pdf.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.pdf.exporter.PdfExporter;
import com.company.framework.pdf.font.PdfFontProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * PDF 오토컨피그 토글/와이어링 + 레지스트레이션 가드.
 *
 * <p>OpenPDF 는 {@code implementation} 이라 테스트 클래스패스에 존재 → {@code @ConditionalOnClass(Document)} 통과.
 * {@code ResourceLoader} 는 {@code ApplicationContextRunner} 의 컨텍스트가 곧 ResourceLoader 라 주입된다.
 */
class PdfAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(PdfAutoConfiguration.class));

    @Test
    @DisplayName("enabled 미지정 → 빈 미등록(기본 off)")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PdfExporter.class);
            assertThat(context).doesNotHaveBean(PdfFontProvider.class);
        });
    }

    @Test
    @DisplayName("enabled=true → PdfExporter + PdfFontProvider 등록(폰트 미설정 → 라틴 폴백)")
    void enabledRegistersBeans() {
        runner.withPropertyValues("framework.pdf.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PdfExporter.class);
            assertThat(context).hasSingleBean(PdfFontProvider.class);
            assertThat(context.getBean(PdfFontProvider.class).hasEmbeddedFont()).isFalse();
        });
    }

    @Test
    @DisplayName("폰트 location 이 없는 경로여도 graceful(폴백) — 컨텍스트 실패 없음")
    void bogusFontLocationIsGraceful() {
        runner.withPropertyValues(
                        "framework.pdf.enabled=true", "framework.pdf.font.location=classpath:fonts/does-not-exist.ttf")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PdfExporter.class);
                    assertThat(context.getBean(PdfFontProvider.class).hasEmbeddedFont())
                            .isFalse();
                });
    }

    /**
     * 레지스트레이션 가드: 위 토글 스모크는 {@code AutoConfigurations.of} 로 클래스를 직접 로드하므로 {@code .imports}
     * 미등록이어도 통과한다(과거 redis 갭이 그렇게 숨었다). 클래스패스의 모든 {@code .imports} 를 직접 읽어 등록을 단언한다.
     */
    @Test
    @DisplayName("PdfAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
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
                .as(".imports 에 PdfAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(PdfAutoConfiguration.class.getName());
    }
}
