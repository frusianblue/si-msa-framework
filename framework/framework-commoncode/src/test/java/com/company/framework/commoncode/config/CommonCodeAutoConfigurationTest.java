package com.company.framework.commoncode.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.commoncode.domain.CommonCode;
import com.company.framework.commoncode.mapper.CommonCodeMapper;
import com.company.framework.commoncode.service.CommonCodeService;
import com.company.framework.commoncode.struct.CommonCodeStructMapper;
import com.company.framework.commoncode.web.CommonCodeController;
import com.company.framework.mybatis.config.MyBatisConfig;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 공통코드 오토컨피그 토글(백오프) + 활성 경로(풀 와이어링) 스모크.
 *
 * <ul>
 *   <li>비활성: {@code framework.commoncode.enabled=false} → 클래스레벨 {@code @MapperScan} 미처리·컨텍스트 정상·빈 0
 *       (완전 하위호환).
 *   <li>활성: 임베디드 H2 + MyBatis 오토컨피그({@link MybatisAutoConfiguration}/{@link MyBatisConfig})가 있으면
 *       {@code @MapperScan} 매퍼·StructMapper·Service·Controller 가 모두 등록되고, 매퍼 XML SQL(insert/select/
 *       update/delete)이 실제 DB 에서 동작한다. snake_case→camelCase 매핑과 감사필드 자동주입
 *       ({@code AuditFieldInterceptor}, 기본 provider→"system")도 함께 검증한다.
 * </ul>
 */
class CommonCodeAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CommonCodeAutoConfiguration.class));

    @Test
    @DisplayName("enabled=false → MapperScan 미처리·컨텍스트 정상·빈 없음")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("framework.commoncode.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CommonCodeService.class);
        });
    }

    private final ApplicationContextRunner enabledRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    MybatisAutoConfiguration.class,
                    MyBatisConfig.class,
                    CommonCodeAutoConfiguration.class))
            .withPropertyValues(
                    "framework.commoncode.enabled=true",
                    "spring.datasource.url=jdbc:h2:mem:commoncode-it;DB_CLOSE_DELAY=-1",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "mybatis.mapper-locations=classpath*:mapper/**/*.xml");

    @Test
    @DisplayName("enabled + H2/MyBatis → 매퍼·서비스·컨트롤러 등록 & 매퍼 XML CRUD 동작")
    void wiresAndRunsMapperWhenEnabled() {
        enabledRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CommonCodeMapper.class);
            assertThat(context).hasSingleBean(CommonCodeStructMapper.class);
            assertThat(context).hasSingleBean(CommonCodeService.class);
            assertThat(context).hasSingleBean(CommonCodeController.class);

            createSchema(context.getBean(DataSource.class));
            CommonCodeMapper mapper = context.getBean(CommonCodeMapper.class);

            CommonCode c = new CommonCode();
            c.setGroupCode("GENDER");
            c.setCode("M");
            c.setCodeName("남성");
            c.setCodeValue("1");
            c.setSortOrder(1);
            c.setUseYn(true);

            assertThat(mapper.insert(c)).isEqualTo(1);
            assertThat(c.getId()).isNotNull(); // useGeneratedKeys
            assertThat(c.getCreatedBy()).isEqualTo("system"); // AuditFieldInterceptor(기본 provider)
            assertThat(c.getCreatedAt()).isNotNull();

            List<CommonCode> found = mapper.findByGroup("GENDER");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).getCodeName()).isEqualTo("남성"); // snake_case→camelCase
            assertThat(mapper.findAllGroups()).containsExactly("GENDER");

            c.setCodeName("남자");
            assertThat(mapper.update(c)).isEqualTo(1);
            assertThat(mapper.findByGroup("GENDER").get(0).getCodeName()).isEqualTo("남자");

            assertThat(mapper.delete("GENDER", "M")).isEqualTo(1);
            assertThat(mapper.findByGroup("GENDER")).isEmpty();
        });
    }

    private static void createSchema(DataSource ds) throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS common_code ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "group_code VARCHAR(64) NOT NULL,"
                    + "code VARCHAR(64) NOT NULL,"
                    + "code_name VARCHAR(255),"
                    + "code_value VARCHAR(255),"
                    + "sort_order INT,"
                    + "use_yn BOOLEAN,"
                    + "attr1 VARCHAR(255),"
                    + "attr2 VARCHAR(255),"
                    + "created_at TIMESTAMP,"
                    + "created_by VARCHAR(64),"
                    + "updated_at TIMESTAMP,"
                    + "updated_by VARCHAR(64))");
        }
    }
}
