package com.company.framework.idgen.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.idgen.code.CodeGenerator;
import com.company.framework.idgen.core.IdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 채번 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.idgen.enabled=true} → {@link IdGenerator}(Snowflake) 등록.
 *   <li>DataSource 부재 → 업무코드 채번({@link CodeGenerator}/SequenceStore) 하위구성은 우아하게 비활성.
 *   <li>기본(미설정) → 어떤 채번 빈도 만들지 않음.
 * </ul>
 *
 * <p>중첩 {@code CodeGeneratorConfiguration} 의 {@code @Bean} 시그니처가 {@code JdbcTemplate}/
 * {@code PlatformTransactionManager} 를 참조하므로(introspection), test 에 {@code spring-boot-starter-jdbc}
 * 를 재선언했다(main 은 {@code compileOnly}). 클래스 존재만 필요 — 빈은 만들지 않는다.
 */
class IdgenAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(IdgenAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true (DataSource 없음) → IdGenerator만 등록, CodeGenerator 비활성")
    void registersIdGeneratorOnly() {
        runner.withPropertyValues("framework.idgen.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IdGenerator.class);
            assertThat(context).doesNotHaveBean(CodeGenerator.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 채번 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdGenerator.class);
        });
    }
}
