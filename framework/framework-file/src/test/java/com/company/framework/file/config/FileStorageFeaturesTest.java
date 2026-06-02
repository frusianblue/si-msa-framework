package com.company.framework.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.file.storage.EncryptingFileStorage;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.FileSystemFileStorage;
import com.company.framework.file.validator.FileContentTypeValidator;
import com.company.framework.file.validator.NoOpFileContentTypeValidator;
import com.company.framework.file.validator.TikaFileContentTypeValidator;
import com.company.framework.mybatis.config.MyBatisConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 파일 보안 기능 배선 검증: 콘텐츠 검증기 선택(NoOp/Tika) + 저장소 at-rest 암호화 래핑(BeanPostProcessor).
 */
class FileStorageFeaturesTest {

    private final FileStorageAutoConfiguration config = new FileStorageAutoConfiguration();

    @Test
    @DisplayName("content-type-detection=false → NoOp 검증기 선택")
    void noOpValidatorByDefault() {
        FileStorageProperties props = new FileStorageProperties();
        assertThat(config.fileContentTypeValidator(props)).isInstanceOf(NoOpFileContentTypeValidator.class);
    }

    @Test
    @DisplayName("content-type-detection=true + tika-core 존재 → Tika 검증기 선택")
    void tikaValidatorWhenEnabledAndPresent() {
        FileStorageProperties props = new FileStorageProperties();
        props.getValidation().setContentTypeDetection(true);
        // tika-core 가 테스트 클래스패스에 있음(testImplementation)
        FileContentTypeValidator v = config.fileContentTypeValidator(props);
        assertThat(v).isInstanceOf(TikaFileContentTypeValidator.class);
    }

    @Test
    @DisplayName("storage.encrypt=true → FileStorage 빈이 EncryptingFileStorage 로 래핑된다")
    void wrapsStorageWithEncryptionWhenEnabled(@TempDir Path tempDir) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        MybatisAutoConfiguration.class,
                        MyBatisConfig.class,
                        FileStorageAutoConfiguration.class))
                .withPropertyValues(
                        "framework.file.enabled=true",
                        "framework.file.storage.type=local",
                        "framework.file.storage.encrypt=true",
                        "framework.file.storage.base-path=" + tempDir.toString().replace('\\', '/'),
                        "spring.datasource.url=jdbc:h2:mem:file-enc-it;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "mybatis.mapper-locations=classpath*:mapper/**/*.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FileStorage storage = context.getBean(FileStorage.class);
                    assertThat(storage).isInstanceOf(EncryptingFileStorage.class);
                    assertThat(((EncryptingFileStorage) storage).delegate()).isInstanceOf(FileSystemFileStorage.class);
                });
    }
}
