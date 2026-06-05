# framework-datasource

DataSource 구성 모듈. 두 가지 **상호 배타적** 기능을 선택형으로 제공한다.

| 기능 | 토글 | 무엇 |
|---|---|---|
| 읽기/쓰기 분리 라우팅 | `framework.datasource.routing.enabled` | 하나의 논리 DB 를 쓰기(primary)/읽기(replica) 노드로 분리, `@Transactional(readOnly=true)` 면 read 로 라우팅 |
| 독립 다중 DB 연결 | `framework.datasource.multi.enabled` | 서로 다른 물리 DB 마다 독립 `DataSource`/`SqlSessionFactory`/트랜잭션 매니저 세트 |

둘 다 기본 비활성(`*.enabled=false`). **두 기능은 동시에 켤 수 없다** — 둘 다 `@Primary` DataSource 를
만들어 충돌하므로, 동시 활성 시 기동 단계에서 명확한 메시지로 **즉시 실패**한다.

---

## 읽기/쓰기 분리 (routing)

`@Transactional(readOnly = true)` 트랜잭션은 read 노드, 그 외에는 write 노드로 커넥션을 라우팅한다
(`LazyConnectionDataSourceProxy` + `AbstractRoutingDataSource`, 결정은 트랜잭션 동기화 시점에 일어남).

```yaml
framework:
  datasource:
    routing:
      enabled: true
      write:
        url: jdbc:postgresql://primary:5432/app
        username: ${DB_USER}
        password: ${DB_PASSWORD}
        maximum-pool-size: 20
      read:
        url: jdbc:postgresql://replica:5432/app
        username: ${DB_USER}
        password: ${DB_PASSWORD}
        maximum-pool-size: 30
```

> 복제 지연(replication lag)이 문제되는 읽기는 같은 트랜잭션 안에서 쓰기와 함께 묶어 write 로 보낸다
> (= `readOnly` 를 빼면 write 로 간다). 읽기 직후 일관성이 꼭 필요한 경로는 readOnly 를 신중히.

---

## 독립 다중 DB (multi)

서로 **다른 물리 DB** 를 한 서비스에서 동시에 쓸 때. DB 키 `<k>` 마다 다음 빈이 등록된다:

| 빈 이름 | 타입 | 앱에서 참조 |
|---|---|---|
| `<k>DataSource` | `HikariDataSource` | (보통 직접 참조 불필요) |
| `<k>SqlSessionFactory` | `SqlSessionFactory` | `@MapperScan(sqlSessionFactoryRef="<k>SqlSessionFactory")` |
| `<k>SqlSessionTemplate` | `SqlSessionTemplate` | (매퍼가 사용) |
| `<k>TransactionManager` | `JdbcTransactionManager` | `@Transactional("<k>TransactionManager")` |

`primary` 로 지정한 키가 `@Primary` 가 된다(소스 1개면 자동, 2개 이상이면 **필수**). `@Primary` 빈은
Boot 기본 DataSource·Flyway·이름 없는 `@Autowired DataSource` 를 해소하고, Boot 의
`DataSourceAutoConfiguration` 은 `@ConditionalOnMissingBean(DataSource)` 으로 물러난다.

### 1) 설정

```yaml
framework:
  datasource:
    multi:
      enabled: true
      primary: order                      # 소스 2개 이상이면 필수
      sources:
        order:
          url: jdbc:postgresql://order-db:5432/orderdb
          username: ${ORDER_DB_USER}
          password: ${ORDER_DB_PASSWORD}
          maximum-pool-size: 20
          type-aliases-package: com.app.order.domain      # 선택(MyBatis)
          mapper-locations: classpath*:mapper/order/*.xml  # 선택(XML 매퍼만)
        user:
          url: jdbc:postgresql://user-db:5432/userdb
          username: ${USER_DB_USER}
          password: ${USER_DB_PASSWORD}
          maximum-pool-size: 10
```

### 2) 앱 측 배선 — DB 별 `@MapperScan`

`@MapperScan` 은 **앱 패키지를 알아야 하므로 프레임워크가 대신 못 한다.** DB 마다 앱이 선언한다:

```java
@Configuration
@MapperScan(
    basePackages = "com.app.order.mapper",
    sqlSessionFactoryRef = "orderSqlSessionFactory")   // <k> = order
class OrderMyBatisConfig {}

@Configuration
@MapperScan(
    basePackages = "com.app.user.mapper",
    sqlSessionFactoryRef = "userSqlSessionFactory")     // <k> = user
class UserMyBatisConfig {}
```

> 매퍼 패키지는 DB 별로 **겹치지 않게** 분리한다(같은 매퍼를 두 팩토리가 잡으면 모호).

### 3) 트랜잭션 — 보조 DB 는 매니저 명시

`@Primary`(여기선 `order`)는 그냥 `@Transactional`. **보조 DB 는 매니저를 명시**한다:

```java
@Transactional                              // primary(order) 트랜잭션
public void placeOrder(...) { ... }

@Transactional("userTransactionManager")    // user DB 트랜잭션
public void updateUserProfile(...) { ... }
```

> 두 DB 를 한 메서드에서 동시에 원자적으로 바꾸려면 분산 트랜잭션이 필요하다 — 본 모듈 범위 밖.
> 교차 DB 정합성은 **framework-saga**(오케스트레이션) 또는 messaging 코레오그래피로 다룬다.

### 4) 마이그레이션

Flyway 등은 `@Primary` DB 에만 자동 적용된다. **보조 DB 마이그레이션은 앱이 별도 구성**한다
(보조 DataSource 를 가리키는 추가 Flyway 빈 등).

---


## 실전 사용 예 (코드)

읽기/쓰기 라우팅은 `DataSourceContextHolder` 로 현재 스레드의 대상 DB 를 지정한다(지정 안 하면 기본 WRITE).
```java
// com.company.framework.datasource.routing.{DataSourceContextHolder, DataSourceType}
public List<Stat> heavyReadReport() {
    DataSourceContextHolder.set(DataSourceType.READ);   // 읽기 복제본으로
    try {
        return statMapper.aggregate();
    } finally {
        DataSourceContextHolder.clear();                 // 반드시 해제
    }
}
```
보조 DB(multi)는 해당 DB 의 트랜잭션 매니저를 명시해야 한다:
```java
@Transactional("secondaryTransactionManager")
public void writeToSecondary(Log row) { secondaryMapper.insert(row); }
```

## 끄는 법
```yaml
framework.datasource.routing.enabled: false   # 기본값(opt-in)
framework.datasource.multi.enabled:   false   # 기본값(opt-in) — routing 과 상호 배타
```
둘 다 끄면(기본) Spring Boot 표준 **단일 `DataSource`** 만 사용한다. 라우팅과 다중 DB 는 둘 중 **하나만** 켜며, 동시에 켜면 fail-fast 로 막는다.

## 일관 동작 (multi)

각 독립 `SqlSessionFactory` 는 단일 DB MyBatis 기본값을 그대로 복제하고, 컨텍스트의 모든
`ConfigurationCustomizer`/`Interceptor` 빈을 적용한다 — 즉 `mapUnderscoreToCamelCase`,
`callSettersOnNulls`, 감사 필드 인터셉터(framework-mybatis) 등이 **모든 DB 에서 동일하게** 걸린다.

## 의존성 (`build.gradle`)

multi 의 MyBatis 빈 생성을 위해 받는 서비스는 mybatis-spring-boot-starter 가 런타임에 있어야 한다
(보통 framework-mybatis 사용 서비스엔 이미 존재). HikariCP 는 Boot starter-jdbc 에 포함.

## 한계 / 다음 후보

- 교차 DB 분산 트랜잭션(2PC/XA)은 비대상 — saga/코레오그래피로 우회.
- 보조 DB Flyway 자동화(현재 앱 책임), DB 별 헬스/메트릭 태깅은 후속 강화 후보.
