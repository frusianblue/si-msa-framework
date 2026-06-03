package com.company.framework.image.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.image.ImageInfo;
import com.company.framework.image.ImageProcessor;
import com.company.framework.image.ResizeSpec;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("ImageAutoConfiguration 자동설정")
class ImageAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ImageAutoConfiguration.class));

    @Test
    @DisplayName("enabled 미지정 → 빈 미등록(기본 off)")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ImageProcessor.class);
        });
    }

    @Test
    @DisplayName("enabled=true → ImageProcessor 등록")
    void enabledRegistersBean() {
        runner.withPropertyValues("framework.image.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ImageProcessor.class);
        });
    }

    @Test
    @DisplayName("프로퍼티 바인딩(default-format/품질/폭탄상한)")
    void bindsProperties() {
        runner.withPropertyValues(
                        "framework.image.enabled=true",
                        "framework.image.default-format=PNG",
                        "framework.image.jpeg-quality=0.6",
                        "framework.image.max-source-pixels=1234567")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ImageProperties props = context.getBean(ImageProperties.class);
                    assertThat(props.getDefaultFormat().name()).isEqualTo("PNG");
                    assertThat(props.getJpegQuality()).isEqualTo(0.6f);
                    assertThat(props.getMaxSourcePixels()).isEqualTo(1234567L);
                });
    }

    @Test
    @DisplayName("앱이 ImageProcessor 빈을 직접 정의하면 그쪽 우선(@ConditionalOnMissingBean)")
    void appProcessorWins() {
        runner.withPropertyValues("framework.image.enabled=true")
                .withUserConfiguration(CustomProcessorConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ImageProcessor.class);
                    assertThat(context.getBean(ImageProcessor.class)).isSameAs(CustomProcessorConfig.CUSTOM);
                });
    }

    @Test
    @DisplayName("ImageAutoConfiguration 이 AutoConfiguration.imports 에 등록돼 있다")
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
                .as(".imports 에 ImageAutoConfiguration 이 등록돼야 자동활성된다")
                .contains(ImageAutoConfiguration.class.getName());
    }

    @Configuration
    static class CustomProcessorConfig {
        static final ImageProcessor CUSTOM = new ImageProcessor() {
            @Override
            public byte[] process(byte[] source, ResizeSpec spec) {
                return new byte[0];
            }

            @Override
            public byte[] thumbnail(byte[] source, int maxEdge) {
                return new byte[0];
            }

            @Override
            public ImageInfo probe(byte[] source) {
                return new ImageInfo(0, 0, "none");
            }
        };

        @Bean
        ImageProcessor imageProcessor() {
            return CUSTOM;
        }
    }
}
