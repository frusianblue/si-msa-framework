package com.company.framework.idgen.config;

import com.company.framework.idgen.code.CodeGenerator;
import com.company.framework.idgen.code.DefaultCodeGenerator;
import com.company.framework.idgen.core.IdGenerator;
import com.company.framework.idgen.core.SnowflakeIdGenerator;
import com.company.framework.idgen.sequence.JdbcTableSequenceStore;
import com.company.framework.idgen.sequence.SequenceStore;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채번 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(IdGenerator) — 이 모듈 의존 시 활성.
 * 2단(기능): framework.idgen.enabled=true.
 * - IdGenerator(Snowflake): 항상 등록(키 없는 분산 ID).
 * - SequenceStore/CodeGenerator(업무코드): DataSource 가 있을 때만 등록(없으면 graceful skip).
 * 3단(override): 모두 @ConditionalOnMissingBean.
 */
@AutoConfiguration
@ConditionalOnClass(IdGenerator.class)
@ConditionalOnProperty(prefix = "framework.idgen", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdgenProperties.class)
public class IdgenAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdgenAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator(IdgenProperties props) {
        long node = props.getSnowflake().getNodeId();
        if (node < 0) {
            node = deriveNodeId();
            log.info(
                    "[idgen] snowflake node-id 미지정 → HOSTNAME 기반 자동 산출: {} " + "(엄격 유일성 필요 시 인스턴스별 node-id 명시 권장)",
                    node);
        }
        return new SnowflakeIdGenerator(node, props.getSnowflake().getEpoch());
    }

    private static long deriveNodeId() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            host = Long.toString(Thread.currentThread().threadId());
        }
        return Math.floorMod(host.hashCode(), 1024);
    }

    /** 업무코드 채번: DataSource(JdbcTemplate) 가 있을 때만. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    static class CodeGeneratorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SequenceStore sequenceStore(
                JdbcTemplate jdbcTemplate, PlatformTransactionManager txManager, IdgenProperties props) {
            JdbcTableSequenceStore store = new JdbcTableSequenceStore(
                    jdbcTemplate,
                    new TransactionTemplate(txManager),
                    props.getSequence().getTableName());
            if (props.getSequence().isInitialize()) {
                store.initSchema();
            }
            return store;
        }

        @Bean
        @ConditionalOnMissingBean
        public CodeGenerator codeGenerator(SequenceStore sequenceStore, IdgenProperties props) {
            return new DefaultCodeGenerator(sequenceStore, props.getSequence().getDefaultPad());
        }
    }
}
