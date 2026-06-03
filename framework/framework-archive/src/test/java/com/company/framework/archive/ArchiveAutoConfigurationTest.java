package com.company.framework.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.archive.config.ArchiveAutoConfiguration;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 아카이빙 오토컨피그 토글/빈 선택/등록 가드 테스트. */
class ArchiveAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ArchiveAutoConfiguration.class));

    @Test
    @DisplayName("기본(미설정)에서는 모듈이 꺼져 Archiver 빈이 없다")
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(Archiver.class));
    }

    @Test
    @DisplayName("framework.archive.enabled=true 면 기본 ZipArchiver 빈이 뜬다")
    void enabledRegistersZipArchiver() {
        runner.withPropertyValues("framework.archive.enabled=true").run(ctx -> assertThat(ctx)
                .hasSingleBean(Archiver.class)
                .getBean(Archiver.class)
                .isInstanceOf(ZipArchiver.class));
    }

    @Test
    @DisplayName("앱이 Archiver 빈을 직접 정의하면 그쪽이 우선한다")
    void appBeanWins() {
        runner.withPropertyValues("framework.archive.enabled=true")
                .withUserConfiguration(CustomArchiverConfig.class)
                .run(ctx -> assertThat(ctx).getBean(Archiver.class).isInstanceOf(CustomArchiver.class));
    }

    @Test
    @DisplayName("등록 가드: .imports 에 ArchiveAutoConfiguration 이 등록되어 있다")
    void registeredInImports() throws Exception {
        String fqcn = "com.company.framework.archive.config.ArchiveAutoConfiguration";
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

    @Configuration
    static class CustomArchiverConfig {
        @Bean
        Archiver archiver() {
            return new CustomArchiver();
        }
    }

    static class CustomArchiver implements Archiver {
        @Override
        public void zip(List<ArchiveEntry> entries, OutputStream out) {}

        @Override
        public void unzip(InputStream zipIn, EntryConsumer consumer) {}

        @Override
        public int unzipToDirectory(InputStream zipIn, Path targetDir) {
            return 0;
        }

        @Override
        public void gzip(InputStream in, OutputStream out) {}

        @Override
        public void gunzip(InputStream in, OutputStream out) {}
    }
}
