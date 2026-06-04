package com.company.framework.qr.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.qr.QrGenerator;
import com.company.framework.qr.QrSpec;
import com.company.framework.qr.ZxingQrGenerator;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** QR 오토컨피그 토글/빈선택/백오프/등록가드. */
class QrAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(QrAutoConfiguration.class));

    @Test
    @DisplayName("enabled 미지정이면 QrGenerator 빈이 없다(기본 false)")
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(QrGenerator.class));
    }

    @Test
    @DisplayName("enabled=true 면 ZxingQrGenerator 빈이 뜬다")
    void registersWhenEnabled() {
        runner.withPropertyValues("framework.qr.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(QrGenerator.class);
            assertThat(ctx.getBean(QrGenerator.class)).isInstanceOf(ZxingQrGenerator.class);
        });
    }

    @Test
    @DisplayName("프로퍼티가 기본 스펙에 반영된다(빈이 정상 동작)")
    void honorsProperties() {
        runner.withPropertyValues(
                        "framework.qr.enabled=true",
                        "framework.qr.default-size-px=320",
                        "framework.qr.default-margin=2",
                        "framework.qr.default-ecc-level=H")
                .run(ctx -> {
                    QrGenerator gen = ctx.getBean(QrGenerator.class);
                    assertThat(gen.toPng("ok")).isNotEmpty();
                });
    }

    @Test
    @DisplayName("앱이 QrGenerator 빈을 직접 정의하면 그쪽이 우선")
    void appBeanWins() {
        runner.withPropertyValues("framework.qr.enabled=true")
                .withUserConfiguration(CustomConfig.class)
                .run(ctx -> assertThat(ctx.getBean(QrGenerator.class)).isInstanceOf(CustomGenerator.class));
    }

    @Test
    @DisplayName("백오프: ZXing 이 클래스패스에 없으면 오토컨피그가 통째로 빠진다")
    void backsOffWhenZxingAbsent() {
        runner.withPropertyValues("framework.qr.enabled=true")
                .withClassLoader(new FilteredClassLoader(QRCodeWriter.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(QrGenerator.class));
    }

    @Test
    @DisplayName("등록 가드: .imports 에 QrAutoConfiguration 이 등록되어 있다")
    void registeredInImports() throws Exception {
        String fqcn = "com.company.framework.qr.config.QrAutoConfiguration";
        var resources = getClass()
                .getClassLoader()
                .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        boolean found = false;
        while (resources.hasMoreElements()) {
            try (InputStream is = resources.nextElement().openStream()) {
                String body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (body.lines().map(String::trim).anyMatch(fqcn::equals)) {
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).as(".imports 에 %s 등록", fqcn).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConfig {
        @Bean
        QrGenerator customGenerator() {
            return new CustomGenerator();
        }
    }

    static class CustomGenerator implements QrGenerator {
        @Override
        public byte[] toPng(String content) {
            return new byte[0];
        }

        @Override
        public byte[] generate(String content, QrSpec spec) {
            return new byte[0];
        }
    }
}
