package com.company.framework.file.s3;

import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.storage.FileStorage;
import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * framework.file.storage.type=s3 일 때 S3 저장소 활성화.
 * endpoint 지정 시 MinIO 등 S3 호환 스토리지에도 사용 가능.
 * 자격증명은 AWS 기본 체인(환경변수/프로파일/IAM Role)을 따른다.
 *
 * <p>{@link S3Presigner} 도 함께 등록해 presigned PUT/GET URL 발급(대용량 직행 업로드/다운로드)을 지원한다.
 * presigner 는 AWS SDK v2 의 {@code s3} 아티팩트에 포함되어 별도 의존성이 필요 없다.
 */
@AutoConfiguration
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "framework.file.storage", name = "type", havingValue = "s3")
public class S3FileStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    public S3Client s3Client(FileStorageProperties props) {
        var s3 = props.getStorage().getS3();
        var builder = S3Client.builder().region(Region.of(s3.getRegion()));
        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(S3Presigner.class)
    public S3Presigner s3Presigner(FileStorageProperties props) {
        var s3 = props.getStorage().getS3();
        var builder = S3Presigner.builder().region(Region.of(s3.getRegion()));
        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage s3FileStorage(S3Client s3Client, S3Presigner s3Presigner, FileStorageProperties props) {
        return new S3FileStorage(
                s3Client, s3Presigner, props.getStorage().getS3().getBucket());
    }
}
