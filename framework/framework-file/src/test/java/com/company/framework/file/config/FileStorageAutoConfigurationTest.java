package com.company.framework.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.file.domain.FileMetadata;
import com.company.framework.file.mapper.FileMapper;
import com.company.framework.file.service.FileService;
import com.company.framework.file.storage.FileStorage;
import com.company.framework.file.storage.FileSystemFileStorage;
import com.company.framework.file.web.FileController;
import com.company.framework.mybatis.config.MyBatisConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 파일 오토컨피그 토글(백오프) + 활성 경로(풀 와이어링) 스모크.
 *
 * <ul>
 *   <li>비활성: {@code framework.file.enabled=false} → 클래스레벨 {@code @MapperScan} 미처리·컨텍스트 정상·빈 0.
 *   <li>활성: 임베디드 H2 + MyBatis 오토컨피그가 있으면 local 저장소(FileSystemFileStorage)·매퍼·Service·Controller 가
 *       모두 등록되고, 매퍼 XML SQL(insert/select/delete)이 실제 DB 에서 동작한다. snake_case→camelCase 매핑과
 *       감사필드 자동주입({@code AuditFieldInterceptor})도 함께 검증한다. (저장 기본경로는 임시 디렉터리로 격리.)
 * </ul>
 */
class FileStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class));

    @Test
    @DisplayName("enabled=false → MapperScan 미처리·컨텍스트 정상·빈 없음")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("framework.file.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FileService.class);
        });
    }

    @Test
    @DisplayName("enabled + H2/MyBatis → 저장소(local)·매퍼·서비스·컨트롤러 등록 & 매퍼 XML CRUD 동작")
    void wiresAndRunsMapperWhenEnabled(@TempDir Path tempDir) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        MybatisAutoConfiguration.class,
                        MyBatisConfig.class,
                        FileStorageAutoConfiguration.class))
                .withPropertyValues(
                        "framework.file.enabled=true",
                        "framework.file.storage.type=local",
                        "framework.file.storage.base-path=" + tempDir.toString().replace('\\', '/'),
                        "spring.datasource.url=jdbc:h2:mem:file-it;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "mybatis.mapper-locations=classpath*:mapper/**/*.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileMapper.class);
                    assertThat(context).hasSingleBean(FileService.class);
                    assertThat(context).hasSingleBean(FileController.class);
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context.getBean(FileStorage.class)).isInstanceOf(FileSystemFileStorage.class);

                    createSchema(context.getBean(DataSource.class));
                    FileMapper mapper = context.getBean(FileMapper.class);

                    FileMetadata meta = new FileMetadata();
                    meta.setOriginalName("report.pdf");
                    meta.setStoredPath("2026/06/03/abc.pdf");
                    meta.setContentType("application/pdf");
                    meta.setSize(2048L);
                    meta.setStorageType("local");

                    assertThat(mapper.insert(meta)).isEqualTo(1);
                    assertThat(meta.getId()).isNotNull(); // useGeneratedKeys
                    assertThat(meta.getCreatedBy()).isEqualTo("system"); // AuditFieldInterceptor

                    Optional<FileMetadata> found = mapper.findById(meta.getId());
                    assertThat(found).isPresent();
                    assertThat(found.get().getOriginalName()).isEqualTo("report.pdf");
                    assertThat(found.get().getStorageType()).isEqualTo("local"); // snake_case→camelCase

                    assertThat(mapper.delete(meta.getId())).isEqualTo(1);
                    assertThat(mapper.findById(meta.getId())).isEmpty();
                });
    }

    private static void createSchema(DataSource ds) throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS file_metadata ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "original_name VARCHAR(255),"
                    + "stored_path VARCHAR(512),"
                    + "content_type VARCHAR(128),"
                    + "size BIGINT,"
                    + "storage_type VARCHAR(32),"
                    + "created_at TIMESTAMP,"
                    + "created_by VARCHAR(64),"
                    + "updated_at TIMESTAMP,"
                    + "updated_by VARCHAR(64))");
        }
    }
}
