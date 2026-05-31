package com.company.framework.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 읽기/쓰기 분리 라우팅 토글 및 노드 설정.
 *
 * <pre>{@code
 * framework:
 *   datasource:
 *     routing:
 *       enabled: false                 # 2단 토글(선택형 → 명시적 on 필요)
 *       write:                         # 주(쓰기) 노드 — 필수
 *         url: jdbc:postgresql://primary:5432/sidb
 *         username: ${DB_USER}
 *         password: ${DB_PASSWORD}
 *         driver-class-name: org.postgresql.Driver   # 보통 url 로 추론되어 생략 가능
 *         maximum-pool-size: 20
 *         minimum-idle: 5
 *         connection-timeout-ms: 3000
 *       read:                          # 복제(읽기) 노드 — 선택. url 비우면 READ 도 WRITE 로 매핑(단일 DB 무해)
 *         url: jdbc:postgresql://replica:5432/sidb
 *         username: ${DB_USER}
 *         password: ${DB_PASSWORD}
 *         maximum-pool-size: 30        # 읽기 부하가 크면 풀을 더 크게
 * }</pre>
 *
 * <p>주의: {@code routing.enabled=true} 면 본 모듈이 {@code @Primary} DataSource 를 직접 만들고 Boot 의
 * {@code DataSourceAutoConfiguration} 은 백오프한다. 즉 이때는 {@code spring.datasource.*} 가 아니라
 * 위 {@code framework.datasource.routing.write/read} 가 실제 접속 정보가 된다. (Flyway 등은 @Primary=WRITE 로 동작)
 */
@ConfigurationProperties(prefix = "framework.datasource.routing")
public class DataSourceRoutingProperties {

    /** 2단 토글. 선택형이라 기본 off — 명시적으로 켜야 라우팅 DataSource 가 활성. */
    private boolean enabled = false;

    /** 주(쓰기) 노드. enabled=true 면 최소한 url 은 필수. */
    private Node write = new Node();

    /** 복제(읽기) 노드. url 이 비어 있으면 READ 키도 write 데이터소스로 매핑된다. */
    private Node read = new Node();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Node getWrite() {
        return write;
    }

    public void setWrite(Node write) {
        this.write = write;
    }

    public Node getRead() {
        return read;
    }

    public void setRead(Node read) {
        this.read = read;
    }

    /** 단일 노드(주 또는 복제) 접속/풀 설정. 미지정 항목은 HikariCP 기본값을 따른다. */
    public static class Node {

        private String url;
        private String username;
        private String password;
        private String driverClassName;

        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Long connectionTimeoutMs;
        private Long maxLifetimeMs;
        private String poolName;

        /** url 이 실제로 설정되어 있는지(공백 제외). */
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
    }
}
