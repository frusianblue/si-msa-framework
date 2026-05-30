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

/**
 * framework.file.storage.type=s3 일 때 S3 저장소 활성화.
 * endpoint 지정 시 MinIO 등 S3 호환 스토리지에도 사용 가능.
 * 자격증명은 AWS 기본 체인(환경변수/프로파일/IAM Role)을 따른다.
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
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage s3FileStorage(S3Client s3Client, FileStorageProperties props) {
        return new S3FileStorage(s3Client, props.getStorage().getS3().getBucket());
    }
}
