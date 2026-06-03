package com.company.framework.file.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.file.config.FileStorageProperties;
import com.company.framework.file.storage.FileStorage;
import java.io.InputStream;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SFTP 오토컨피그 토글/빈선택/백오프/등록가드. 빈 생성은 {@code SshClient.start()} 까지만 하고 실제 연결은
 * 작업 시점에 하므로, 더미 호스트로도 컨텍스트가 뜬다(네트워크 불필요).
 */
class SftpFileStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SftpFileStorageAutoConfiguration.class))
            .withUserConfiguration(PropsConfig.class)
            // 더미 연결정보 + strict off(테스트 환경 known_hosts 의존 제거)
            .withPropertyValues(
                    "framework.file.storage.sftp.host=localhost",
                    "framework.file.storage.sftp.username=tester",
                    "framework.file.storage.sftp.password=secret",
                    "framework.file.storage.sftp.strict-host-key-checking=false");

    @Test
    @DisplayName("type 미지정이면 SFTP 저장소 빈이 없다")
    void noBeanWhenTypeNotSftp() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(FileStorage.class));
    }

    @Test
    @DisplayName("type=sftp 면 SftpFileStorage 빈이 뜬다")
    void registersWhenTypeSftp() {
        runner.withPropertyValues("framework.file.storage.type=sftp").run(ctx -> {
            assertThat(ctx).hasSingleBean(FileStorage.class);
            FileStorage fs = ctx.getBean(FileStorage.class);
            assertThat(fs).isInstanceOf(SftpFileStorage.class);
            assertThat(fs.type()).isEqualTo("sftp");
        });
    }

    @Test
    @DisplayName("앱이 FileStorage 빈을 직접 정의하면 그쪽이 우선한다")
    void appBeanWins() {
        runner.withPropertyValues("framework.file.storage.type=sftp")
                .withUserConfiguration(CustomStorageConfig.class)
                .run(ctx -> assertThat(ctx.getBean(FileStorage.class)).isInstanceOf(CustomStorage.class));
    }

    @Test
    @DisplayName("백오프: sshd-sftp 가 클래스패스에 없으면 오토컨피그가 통째로 빠진다")
    void backsOffWhenSftpClassAbsent() {
        runner.withPropertyValues("framework.file.storage.type=sftp")
                .withClassLoader(new FilteredClassLoader(SftpClient.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(FileStorage.class));
    }

    @Test
    @DisplayName("등록 가드: .imports 에 SftpFileStorageAutoConfiguration 이 등록되어 있다")
    void registeredInImports() throws Exception {
        String fqcn = "com.company.framework.file.sftp.SftpFileStorageAutoConfiguration";
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
    @EnableConfigurationProperties(FileStorageProperties.class)
    static class PropsConfig {}

    @Configuration(proxyBeanMethods = false)
    static class CustomStorageConfig {
        @Bean
        FileStorage customStorage() {
            return new CustomStorage();
        }
    }

    static class CustomStorage implements FileStorage {
        @Override
        public com.company.framework.file.storage.StoredFile store(
                InputStream content, String originalName, String contentType, long size) {
            return null;
        }

        @Override
        public InputStream load(String storedPath) {
            return null;
        }

        @Override
        public void delete(String storedPath) {}

        @Override
        public String type() {
            return "custom";
        }
    }
}
