# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**`framework-datasource` 에 독립 다중 DB(`multi.*`) 완성.** 서로 다른 물리 DB 마다 `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager` 4빈 세트를 **`ImportBeanDefinitionRegistrar`** 로 동적 등록(키 개수가 런타임 설정이라 정적 `@Bean` 불가). `@AutoConfiguration(before=DataSourceAutoConfiguration)` + `@Primary` 로 Boot `@ConditionalOnMissingBean(DataSource)` 백오프 유지. 기존 읽기/쓰기 분리(`routing.*`)와는 **상호 배타**(둘 다 `@Primary` DataSource → 충돌, 기동 시 fail-fast). 순수 결정/검증 로직(primary 키·충돌·빈이름 규약)은 Spring 무의존 `MultiDataSourcePlan` 으로 분리해 JDK **13/13** 실행검증. `@MapperScan`/`@Transactional("<k>TransactionManager")` 는 앱이 배선(프레임워크가 앱 패키지를 모름). **새 외부 의존성 0**.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **`framework-datasource` 확장**(신규 모듈 아님, 기존 모듈에 `multi.*` 서브기능 추가). prefix `framework.datasource.multi.*`.
- **순수 코어**(Spring/MyBatis 무의존, `…datasource.multi.MultiDataSourcePlan`): `resolvePrimaryKey(keys, configuredPrimary)`(소스1개=자동·2개이상=primary필수·미존재키/빈칸/빈소스→ISE), `assertNotConflictingWithRouting(routingEnabled)`(routing 동시활성→ISE), 빈이름 규약 헬퍼(`<k>DataSource`/`SqlSessionFactory`/`SqlSessionTemplate`/`TransactionManager`).
- **`config/MultiDataSourceProperties`**: `framework.datasource.multi.{enabled, primary, sources.<k>.{url,username,password,driver-class-name,maximum-pool-size,minimum-idle,connection-timeout-ms,max-lifetime-ms,pool-name,type-aliases-package,mapper-locations[]}}`. `Map` 입력순서 보존(LinkedHashMap).
- **`multi/MultiDataSourceSupport`**(package-private 정적 빌더): `buildDataSource(Source,key)`→HikariDataSource, `buildSqlSessionFactory(ds, source, ObjectProvider<ConfigurationCustomizer>, ObjectProvider<Interceptor>)`→ibatis Configuration 기본값(single-DB 복제) + 모든 customizer/Interceptor 적용 + typeAliasesPackage/mapperLocations.
- **`multi/MultiDataSourceRegistrar`** implements `ImportBeanDefinitionRegistrar`, `EnvironmentAware`, `BeanFactoryAware`: Binder 로 props 바인딩(`.bind("framework.datasource.multi", MultiDataSourceProperties.class)` — 레포 표준 Class 오버로드), routing 충돌 검사, primary 키 결정, 키별 4빈 정의를 `BeanDefinitionBuilder.genericBeanDefinition(Class, Supplier)` + `setDependsOn`/`setPrimary` 로 등록(DataSource 는 `setDestroyMethodName("close")`). 협력자는 캡처한 `ConfigurableListableBeanFactory` 에서 by-name 해소.
- **`config/MultiDataSourceAutoConfiguration`**: `@AutoConfiguration(before=DataSourceAutoConfiguration.class, beforeName="…MybatisAutoConfiguration")` + `@ConditionalOnClass({SqlSessionFactoryBean, HikariDataSource})` + `@ConditionalOnProperty(multi.enabled=true)` + `@EnableConfigurationProperties` + `@Import(Registrar)`. 본문 빈.
- **imports** 등록(2엔트리=routing+multi). **build.gradle**: `compileOnly mybatis-spring-boot-starter`(MyBatis 빈) 추가, test 에 `testImplementation mybatis-spring-boot-starter` + `testRuntimeOnly h2`.
- **테스트**: `MultiDataSourcePlanTest`(순수 7케이스) + `MultiDataSourceAutoConfigurationTest`(ApplicationContextRunner 6케이스=빈세트 등록·primary 타입해소+Boot백오프·tx매니저별 DataSource 배선·물리 독립성(H2 2개)·routing+multi 충돌 fail-fast·다중소스 primary 누락 fail-fast).
- **문서**: 신규 `framework/framework-datasource/README.md` + 핸드오프 4종(이 문서/HANDOFF/README root/FRAMEWORK_MODULES) 동기화.

## 현재 상태 (적용/검증)
- **순수 결정/검증 로직 JDK(JDK21) 13/13**: 단일소스 자동primary·빈칸primary·명시primary·빈소스/다중무primary/다중빈칸/미존재키 ISE·routing충돌 ISE·routing-off 통과·빈이름 규약 4종.
- 정적 점검 ALL PASS: `com.fasterxml.jackson` **0건**(이 모듈 JSON 무관), 패키지=디렉터리 12파일 일치, 중괄호 균형. FQCN 교차검증: `ConfigurationCustomizer`(framework-mybatis 동일), Binder Class-오버로드(framework-observability 동일), `DataSourceAutoConfiguration`(기존 routing 모듈 동일).
- ⚠️ Spring 어댑터/오토컨피그의 **gradle 컴파일·단위테스트는 받는 쪽에서**(작성 환경 Maven Central 차단). 받는 쪽: `./gradlew :framework:framework-datasource:compileJava :framework:framework-datasource:test (+spotlessApply)` — ApplicationContextRunner 6 + plan 7 그린 확인.

## 켜는 법
- 서비스 `application.yml`: `framework.datasource.multi.enabled=true`, `sources.<k>.{url,username,password,...}`, 소스 2개 이상이면 `primary: <k>` **필수**. **routing 과 동시 활성 금지**(fail-fast).
- 앱이 DB 별로 `@Configuration @MapperScan(basePackages="…", sqlSessionFactoryRef="<k>SqlSessionFactory")` 선언(매퍼 패키지 DB 별 분리). 보조 DB 트랜잭션은 `@Transactional("<k>TransactionManager")`, primary 는 무인자 `@Transactional`.
- 받는 서비스에 mybatis-spring-boot-starter 런타임 존재(보통 framework-mybatis 사용 서비스엔 이미 있음). Flyway 는 `@Primary` DB 만 자동 — 보조 DB 마이그레이션은 앱이 별도.

## 바로 다음 할 일 (Next)
1. 받는 쪽 `:framework:framework-datasource:compileJava :test (+spotlessApply)` — ApplicationContextRunner 6 + plan 7 그린 + `-Xlint` 경고 확인.
2. (선택·devops) 실DB(H2/PostgreSQL) 2개 물리 독립성·보조 DB 트랜잭션 격리 e2e, 보조 DB Flyway 자동화(현재 앱 책임) 레시피화.
3. (선택) DB 별 헬스/메트릭 태깅(observability 연계: `<k>` 태그), 풀 메트릭 노출.
4. **그릇 정비** 잔여: 게이트웨이 런타임 점검(CORS preflight/rate-limit 429) · k8s 멀티서비스/CI-CD · (선택) 규제특화 잔여(pki/hsm/recon/egov).

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **동적 빈은 `ImportBeanDefinitionRegistrar`(BDRPP 아님).** 키 개수가 런타임 설정이라 정적 `@Bean` 불가. **Registrar 인 이유**: `BeanDefinitionRegistryPostProcessor` 는 `ConfigurationClassPostProcessor` 이후라 `@AutoConfiguration` before-순서를 못 지킴 → Boot `@ConditionalOnMissingBean(DataSource)` 가 우리 `@Primary` 정의를 못 봐 충돌. Registrar 는 config-class 파싱 중 실행돼 before-순서 보존 → Boot 백오프.
- **routing 과 multi 는 상호 배타** — 둘 다 `@Primary` DataSource. Registrar 가 `framework.datasource.routing.enabled` 읽어 **기동 시 fail-fast**.
- **`@MapperScan` 은 프레임워크가 못 함**(앱 패키지 의존) → 앱이 DB 별 `sqlSessionFactoryRef="<k>SqlSessionFactory"` 로 선언. 보조 DB 는 `@Transactional("<k>TransactionManager")` 매니저 명시(primary 만 무인자).
- **빈이름 규약**: `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager`. primary 키는 소스1개면 자동·2개이상이면 필수(누락/빈칸/미존재→fail-fast). `@Primary` 가 Boot 기본DS·Flyway·이름없는 `@Autowired DataSource` 해소. **Flyway 는 primary DB 만** 자동.
- **독립 SqlSessionFactory 도 single-DB 동작 복제** — ibatis 기본값 + 컨텍스트의 모든 `ConfigurationCustomizer`/`Interceptor`(framework-mybatis 감사 포함) 적용 → 전 DB 매핑/감사 동일. Registrar 는 `EnvironmentAware`/`BeanFactoryAware` 주입 가능, Supplier 에서 `ConfigurableListableBeanFactory` by-name 해소.
- (지난) `compileOnly` 비전이(테스트/의존 서비스 재선언) · 새 의존성 0(Boot BOM) · 모듈 toggle 3단 기본 off · 순수 로직 분리해 JDK 단독 검증 · Jackson3(`tools.jackson.*`, `com.fasterxml.*` 금지) · 필터/리스너 컨테이너는 앱 소유 · `[this-escape]` 생성자 메서드참조 금지.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. **순수 로직(상태머신·코덱·선점·결정)은 Spring/MyBatis 무의존 코어로 분리**하고 SPI/플랜으로 경계를 그으면 인메모리/JDK 단독 검증 가능(이번엔 `MultiDataSourcePlan` 13케이스).
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(messaging/kafka/jdbc/redis/web/**mybatis**). **테스트나 의존 서비스가 그 compileOnly 클래스를 쓰면 재선언**.
3. `settings.gradle`/`imports` 등록(신규 모듈이면 필수; 기존 모듈 확장이면 imports 엔트리만 추가).
4. 코드 전 Boot4/Spring7/Jackson3 + 통합 대상(MyBatis ConfigurationCustomizer·Boot DataSourceAutoConfiguration 등) 실제 시그니처/FQCN 레포 내 동일 사용처로 교차확인. 조용히 틀리는 결정 로직은 순수 JDK 또는 H2 로 실행 검증.
5. 오토컨피그: `@AutoConfiguration`(필요 시 `before`/`beforeName` 으로 Boot 백오프 순서) + `@ConditionalOnClass/Property` 3단 + 빈 `@ConditionalOnMissingBean`. 런타임 개수 가변 빈은 `ImportBeanDefinitionRegistrar`.
6. 검증: `./gradlew :…:compileJava (+:test) (+spotlessApply)` — 경고(`this-escape`/`-Xlint`)도 확인.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
