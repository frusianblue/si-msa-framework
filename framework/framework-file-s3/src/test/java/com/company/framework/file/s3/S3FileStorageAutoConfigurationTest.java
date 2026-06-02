package com.company.framework.file.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 저장소 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.file.storage.type 미지정} → S3Client/FileStorage 미등록(기본 비활성).
 *   <li>{@code type=s3} → {@link FileStorage}(S3FileStorage) 등록.
 * </ul>
 *
 * <p>{@code S3FileStorageAutoConfiguration} 은 {@code FileStorageProperties} 를 직접 {@code @EnableConfigurationProperties}
 * 하지 않고 file 모듈이 노출하는 빈을 주입받으므로(여기선 deep-stub mock 제공), 또 실제 AWS 연결을 피하기 위해
 * {@link S3Client} mock 을 제공한다({@code s3Client @Bean} 은 {@code @ConditionalOnMissingBean(S3Client)} 로 양보).
 */
class S3FileStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(S3FileStorageAutoConfiguration.class))
            .withBean(S3Client.class, () -> mock(S3Client.class))
            .withBean(FileStorageProperties.class, () -> mock(FileStorageProperties.class, Answers.RETURNS_DEEP_STUBS));

    @Test
    @DisplayName("type=s3 → S3FileStorage(FileStorage) 등록")
    void registersS3StorageWhenTypeS3() {
        runner.withPropertyValues("framework.file.storage.type=s3").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FileStorage.class);
            assertThat(context.getBean(FileStorage.class)).isInstanceOf(S3FileStorage.class);
        });
    }

    @Test
    @DisplayName("type 미지정 → S3 빈 미등록")
    void backsOffByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FileStorage.class);
        });
    }
}
