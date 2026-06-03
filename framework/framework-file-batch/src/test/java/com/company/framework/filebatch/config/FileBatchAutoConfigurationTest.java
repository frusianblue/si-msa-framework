package com.company.framework.filebatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.archive.Archiver;
import com.company.framework.archive.ZipArchiver;
import com.company.framework.filebatch.FileBatchProcessor;
import com.company.framework.filebatch.ops.BatchArchiveOps;
import com.company.framework.filebatch.ops.BatchImageOps;
import com.company.framework.image.ImageInfo;
import com.company.framework.image.ImageProcessor;
import com.company.framework.image.ResizeSpec;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 파일 일괄처리 오토컨피그 검증: 3단 토글 / 앱빈 우선 / .imports 등록 가드 /
 * 위임 모듈 백오프({@code @ConditionalOnClass} 클래스 부재, {@code @ConditionalOnBean} 빈 부재).
 */
class FileBatchAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FileBatchAutoConfiguration.class));

    @Test
    @DisplayName("기본(미설정)에서는 모듈이 꺼져 FileBatchProcessor 빈이 없다")
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(FileBatchProcessor.class));
    }

    @Test
    @DisplayName("enabled=true 면 FileBatchProcessor 가 뜨고 default-parallelism 이 반영된다")
    void enabledRegistersProcessor() {
        runner.withPropertyValues("framework.file-batch.enabled=true", "framework.file-batch.default-parallelism=7")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FileBatchProcessor.class);
                    assertThat(ctx.getBean(FileBatchProcessor.class)
                                    .defaultOptions()
                                    .parallelism())
                            .isEqualTo(7);
                });
    }

    @Test
    @DisplayName("앱이 FileBatchProcessor 빈을 직접 정의하면 그쪽이 우선한다")
    void appBeanWins() {
        FileBatchProcessor custom = new FileBatchProcessor(1);
        runner.withPropertyValues("framework.file-batch.enabled=true")
                .withBean(FileBatchProcessor.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(FileBatchProcessor.class)).isSameAs(custom));
    }

    @Test
    @DisplayName("위임 빈 등록: ImageProcessor/Archiver 빈이 있으면 BatchImageOps/BatchArchiveOps 가 함께 뜬다")
    void registersDelegateFactoriesWhenBeansPresent() {
        runner.withPropertyValues("framework.file-batch.enabled=true")
                .withUserConfiguration(DelegateBeans.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(BatchImageOps.class);
                    assertThat(ctx).hasSingleBean(BatchArchiveOps.class);
                });
    }

    @Test
    @DisplayName("백오프(빈 부재): 위임 클래스가 있어도 ImageProcessor/Archiver 빈이 없으면 팩토리도 없다")
    void backsOffWhenDelegateBeansAbsent() {
        runner.withPropertyValues("framework.file-batch.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(FileBatchProcessor.class);
            assertThat(ctx).doesNotHaveBean(BatchImageOps.class);
            assertThat(ctx).doesNotHaveBean(BatchArchiveOps.class);
        });
    }

    @Test
    @DisplayName("백오프(클래스 부재): framework-image 가 클래스패스에 없으면 ImageOps 설정이 통째로 빠진다")
    void backsOffWhenImageClassAbsent() {
        // ImageProcessor 를 가리므로, ImageProcessor 를 참조하는 설정은 등록할 수 없다 → archive 빈만 제공.
        runner.withPropertyValues("framework.file-batch.enabled=true")
                .withUserConfiguration(ArchiverBean.class)
                .withClassLoader(new FilteredClassLoader(ImageProcessor.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FileBatchProcessor.class);
                    assertThat(ctx).doesNotHaveBean(BatchImageOps.class);
                    // archive 는 그대로 살아 있어야 한다(독립적 백오프)
                    assertThat(ctx).hasSingleBean(BatchArchiveOps.class);
                });
    }

    @Test
    @DisplayName("백오프(클래스 부재): framework-archive 가 없으면 ArchiveOps 설정만 빠지고 나머지는 유지된다")
    void backsOffWhenArchiveClassAbsent() {
        runner.withPropertyValues("framework.file-batch.enabled=true")
                .withUserConfiguration(ImageProcessorBean.class)
                .withClassLoader(new FilteredClassLoader(Archiver.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FileBatchProcessor.class);
                    assertThat(ctx).doesNotHaveBean(BatchArchiveOps.class);
                    assertThat(ctx).hasSingleBean(BatchImageOps.class);
                });
    }

    @Test
    @DisplayName("등록 가드: .imports 에 FileBatchAutoConfiguration 이 등록되어 있다")
    void registeredInImports() throws Exception {
        String fqcn = "com.company.framework.filebatch.config.FileBatchAutoConfiguration";
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

    /** 위임 모듈 빈을 제공하는 사용자 설정(테스트 클래스패스엔 image/archive 가 있다). */
    @Configuration(proxyBeanMethods = false)
    static class DelegateBeans {
        @Bean
        ImageProcessor imageProcessor() {
            return new NoopImageProcessor();
        }

        @Bean
        Archiver archiver() {
            return new ZipArchiver(100, 1_000_000L, 10_000_000L);
        }
    }

    /** 이미지 처리기 빈만 제공(archive 클래스 필터링 테스트용 — Archiver 를 참조하지 않는다). */
    @Configuration(proxyBeanMethods = false)
    static class ImageProcessorBean {
        @Bean
        ImageProcessor imageProcessor() {
            return new NoopImageProcessor();
        }
    }

    /** Archiver 빈만 제공(image 클래스 필터링 테스트용 — ImageProcessor 를 참조하지 않는다). */
    @Configuration(proxyBeanMethods = false)
    static class ArchiverBean {
        @Bean
        Archiver archiver() {
            return new ZipArchiver(100, 1_000_000L, 10_000_000L);
        }
    }

    /** 동작 없는 이미지 처리기(빈 존재 조건 충족용). */
    static class NoopImageProcessor implements ImageProcessor {
        @Override
        public byte[] process(byte[] source, ResizeSpec spec) {
            return source;
        }

        @Override
        public byte[] thumbnail(byte[] source, int maxEdge) {
            return source;
        }

        @Override
        public ImageInfo probe(byte[] source) {
            return null;
        }
    }
}
