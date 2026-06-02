package com.company.framework.datasource.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서로 다른 <b>독립 DB 다중 연결</b> 설정. DB 마다 별도 {@code DataSource} / {@code SqlSessionFactory} /
 * {@code SqlSessionTemplate} / 트랜잭션 매니저 세트를 만든다(읽기/쓰기 분리인 {@code routing.*} 과는 별개·배타).
 *
 * <pre>{@code
 * framework:
 *   datasource:
 *     multi:
 *       enabled: true
 *       primary: order                 # @Primary 로 삼을 키(소스 2개 이상이면 필수). Boot 기본 DS·Flyway·이름없는 @Autowired DataSource 가 이 키로 해소.
 *       sources:
 *         order:
 *           url: jdbc:postgresql://order-db:5432/orderdb
 *           username: ${ORDER_DB_USER}
 *           password: ${ORDER_DB_PASSWORD}
 *           maximum-pool-size: 20
 *           type-aliases-package: com.app.order.domain          # 선택(MyBatis)
 *           mapper-locations: classpath*:mapper/order/*.xml      # 선택(MyBatis XML; 어노테이션 매퍼만 쓰면 생략)
 *         user:
 *           url: jdbc:postgresql://user-db:5432/userdb
 *           username: ${USER_DB_USER}
 *           password: ${USER_DB_PASSWORD}
 * }</pre>
 *
 * <p>각 키 {@code <k>} 에 대해 다음 빈이 등록된다(앱이 참조):
 *
 * <ul>
 *   <li>{@code <k>DataSource} (HikariDataSource)
 *   <li>{@code <k>SqlSessionFactory} — {@code @MapperScan(sqlSessionFactoryRef="<k>SqlSessionFactory")} 로 지정
 *   <li>{@code <k>SqlSessionTemplate}
 *   <li>{@code <k>TransactionManager} — {@code @Transactional("<k>TransactionManager")} 로 지정
 * </ul>
 *
 * <p>주의: {@code @MapperScan} 은 앱 패키지를 알아야 하므로 <b>프레임워크가 대신 할 수 없다</b> — 앱이 DB 별로 선언한다.
 * Flyway 등 마이그레이션은 {@code @Primary} DB 에만 자동 적용되며, 보조 DB 마이그레이션은 앱이 별도 구성한다.
 */
@ConfigurationProperties(prefix = "framework.datasource.multi")
public class MultiDataSourceProperties {

    /** 2단 토글. 선택형이라 기본 off. */
    private boolean enabled = false;

    /** {@code @Primary} 로 삼을 소스 키. 소스가 1개면 생략 가능(그 1개가 자동 primary), 2개 이상이면 필수. */
    private String primary;

    /** DB 키 → 접속/풀/MyBatis 설정. 입력 순서를 보존한다. */
    private Map<String, Source> sources = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public Map<String, Source> getSources() {
        return sources;
    }

    public void setSources(Map<String, Source> sources) {
        this.sources = sources;
    }

    /** 단일 독립 DB 의 접속·풀(HikariCP) + MyBatis(선택) 설정. 미지정 풀 항목은 HikariCP 기본값을 따른다. */
    public static class Source {

        private String url;
        private String username;
        private String password;
        private String driverClassName;

        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Long connectionTimeoutMs;
        private Long maxLifetimeMs;
        private String poolName;

        /** MyBatis typeAliases 스캔 패키지(선택). */
        private String typeAliasesPackage;

        /** MyBatis XML 매퍼 위치 패턴 목록(선택). 어노테이션 매퍼만 쓰면 비워 둔다. */
        private java.util.List<String> mapperLocations = new java.util.ArrayList<>();

        public boolean hasUrl() {
            return url != null && !url.isBlank();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Integer getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(Integer maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public Integer getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(Integer minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public Long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(Long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public Long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(Long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public String getTypeAliasesPackage() {
            return typeAliasesPackage;
        }

        public void setTypeAliasesPackage(String typeAliasesPackage) {
            this.typeAliasesPackage = typeAliasesPackage;
        }

        public java.util.List<String> getMapperLocations() {
            return mapperLocations;
        }

        public void setMapperLocations(java.util.List<String> mapperLocations) {
            this.mapperLocations = mapperLocations;
        }
    }
}
