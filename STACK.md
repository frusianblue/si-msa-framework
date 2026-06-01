# STACK.md — 라이브러리 / 플러그인 관리

> 목적: 무엇을, 왜, 어디에, 어떤 버전으로 쓰는지 한곳에서 추적한다.
> 단일 버전 소스는 `gradle/libs.versions.toml`. **버전을 바꿀 땐 카탈로그를 고치고 이 표를 갱신**한다.
> 최종 갱신: 2026-06-01 · 갱신자: <!-- 채우기 -->

---

## 1. 버전 정책
- 모든 버전은 `gradle/libs.versions.toml` 가 단일 소스. (`gradle.properties` 의 버전 라인은 제거)
- 루트 `build.gradle` 의 `ext { }` 브리지로 기존 모듈의 `${...Version}` 참조가 그대로 동작 → 모듈은 `libs.*` 로 점진 이관.
- Boot BOM(`spring-boot-dependencies`)이 관리하는 라이브러리는 **버전을 적지 않는다**(BOM 위임). 카탈로그엔 BOM 밖 라이브러리/플러그인만 버전 명시.

## 2. 적용된 Gradle 플러그인
| 플러그인 id | 버전 | 용도 | 적용 위치 |
|---|---|---|---|
| `org.springframework.boot` | 4.0.6 | Boot 빌드/패키징(bootJar, 레이어, bootBuildImage, SBOM) | 루트(apply false) → 서비스 모듈 |
| `io.spring.dependency-management` | 1.1.7 | BOM 기반 의존성 버전 관리 | 전 모듈 |
| `java-library` | (내장) | 라이브러리 모듈 컴파일 | 전 서브프로젝트 |
| `jacoco` | (내장) | 테스트 커버리지 → Sonar 연동 | 전 서브프로젝트 |
| `com.diffplug.spotless` | 8.5.1 | 코드 포맷(Palantir Java Format) 게이트 | 전 서브프로젝트 |
| `org.owasp.dependencycheck` | 12.1.0 | 의존성 CVE 스캔(CVSS 7.0+ 빌드 실패) | 루트(aggregate) |
| `org.sonarqube` | 6.0.1.5171 | 정적분석/보안 핫스팟/커버리지 수집 | 루트 |
| `org.flywaydb.flyway` | 11.15.0 | CI 마이그레이션(flywayValidate/Info/Migrate) | user/admin 서비스 |

> 플러그인 최신 버전 확인: https://plugins.gradle.org/ (플러그인 id 검색)

## 3. 런타임 라이브러리
### 3.1 BOM 밖(카탈로그에서 버전 고정)
| 라이브러리 | 버전 | 용도 | 적용 위치 |
|---|---|---|---|
| `mybatis-spring-boot-starter` | 4.0.1 | MyBatis 연동 | framework-mybatis |
| `io.jsonwebtoken:jjwt-*` | 0.12.6 | JWT 발급/검증 | framework-security |
| `springdoc-openapi-starter-webmvc-ui` | 3.0.3 | Swagger UI / OpenAPI | framework-openapi |
| `org.mapstruct:mapstruct(+processor)` | 1.6.3 | DTO 컴파일타임 변환(런타임 비용 0) | framework-commoncode 등 |
| `org.projectlombok:lombok-mapstruct-binding` | 0.2.0 | Lombok+MapStruct 병행 | (MapStruct 쓰는 모듈) |
| `software.amazon.awssdk:bom` / `s3` | 2.31.0 | S3 저장소 (Spring Cloud AWS 회피) | framework-file-s3 |
| `com.github.gavlyukovskiy:datasource-proxy-spring-boot-starter` | 2.0.0 | SQL 디버깅(바인딩 값/슬로우쿼리) — **Boot 4 는 2.0.0+** | 서비스(개발) |
| `org.apache.poi:poi-ooxml` | 5.5.1 | Excel 업/다운로드(XSSF/SXSSF/HSSF) — **Boot BOM 미관리**(여기서 고정), 모듈 내부 implementation | framework-excel(선택) |

### 3.2 BOM 관리(버전 미명시)
| 라이브러리 | 용도 | 적용 위치 |
|---|---|---|
| `spring-boot-starter-web/validation/actuator` | 웹/검증/관측 | framework-core |
| `spring-boot-starter-aspectj` | AOP (Boot 4: starter-aop 개명) | framework-core |
| `spring-boot-starter-cache` + `caffeine` | 공통 캐시 | framework-core |
| `micrometer-tracing-bridge-otel` | 분산추적(traceId/spanId) | framework-core, gateway |
| `spring-boot-starter-security` | 인증/인가 | framework-security |
| `spring-boot-starter-data-redis` | Redis TokenStore + 로그인 시도 잠금 저장소(다중 인스턴스 공유) | framework-redis(선택) |
| `org.springframework.kafka:spring-kafka` | 신뢰성 발행(Transactional Outbox 릴레이) — Boot BOM 관리(버전 미명시) | framework-messaging(선택) |
| `spring-boot-starter-batch` | 배치 실행/리스너(Spring Batch 6 — Boot 4) — Boot BOM 관리 | framework-batch(선택) |
| `spring-boot-starter-quartz` | Quartz cron 스케줄러(기본 RAM JobStore) — Boot BOM 관리 | framework-batch(선택) |
| `spring-boot-starter-mail` | 메일 채널(JavaMailSender, jakarta.mail) — Boot BOM 관리 | framework-notification(선택) |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | DB 마이그레이션(PG 10+) | 서비스 |
| `org.postgresql:postgresql` | 운영 DB 드라이버(프레임워크는 미포함) | 각 서비스 |
| `com.h2database:h2` | 로컬/테스트 DB | 각 서비스 |
| `spring-cloud-starter-gateway-server-webflux` | API 게이트웨이(Boot 4 아티팩트) | gateway |
| `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | 회로차단 | gateway |

> **공통기능 토대 4종(2026-05: idempotency·i18n·idgen·client) + 보안 완성(framework-audit·framework-secure-web, framework-security 확장)은 새 버전 의존성을 추가하지 않는다.**
> 모두 `framework-core`/`framework-security` + (필요 시) `spring-boot-starter-web/jdbc/data-redis` 를 `compileOnly`(호스트 제공)로만 사용하고,
> 서킷브레이커는 자체 구현, 거부 응답 JSON 은 수기 직렬화(Jackson 비의존). 따라서 `libs.versions.toml` 변경 없음. 향후 messaging(Kafka)/batch 등 BOM 밖 라이브러리가
> 필요한 모듈을 추가할 때 비로소 이 표에 행을 추가한다.

## 4. 테스트 / 개발 도구
| 항목 | 버전 | 용도 | 적용 위치 |
|---|---|---|---|
| `spring-boot-starter-test` | BOM | JUnit5/AssertJ/Mockito | 전 모듈 test |
| `spring-boot-testcontainers` | BOM | 실 PostgreSQL 통합테스트(@ServiceConnection) | 서비스 test |
| `org.testcontainers:junit-jupiter` / `postgresql` | BOM | 컨테이너 기동/PG 모듈 | 서비스 test |
| `spring-security-test` | BOM | 보안 테스트 | framework-security test |
| `spring-boot-devtools` | BOM | 핫 리로드(developmentOnly) | 서비스 |

## 5. Boot 4 호환 주의 (되돌리지 말 것)
- **Gradle 8.14+** 필수 (Boot 4 Gradle 플러그인 요구사항).
- **Jackson 3** (`tools.jackson.*`) — 커스터마이저는 `JsonMapperBuilderCustomizer`, 매퍼는 `JsonMapper`. ⚠️ `com.fasterxml.jackson.core/databind` import 금지(클래스패스에 없음 → 컴파일 에러; 특히 `com.fasterxml.jackson.databind.ObjectMapper`). 단 **애너테이션**(`@JsonInclude` 등)은 Jackson 3 에서도 `com.fasterxml.jackson.annotation` 패키지 유지 → OK. 필터/인프라 레벨의 단순 JSON 응답은 Jackson 빈 주입 대신 수기 직렬화가 견고(`SecureWebResponder` 사례).
- **Spring Security 7** — `AuthorizationManager.authorize()` (구 `check()` 제거).
- **Gateway** 아티팩트 `spring-cloud-starter-gateway-server-webflux`.
- **Spring Cloud 2025.1.x(Oakwood)** — 2025.0.x 는 Boot 4 비호환.
- **datasource-proxy / p6spy 스타터** — 반드시 `2.0.0+`.
- **Spring Cloud AWS 미사용** — Jackson2 의존 회피 위해 AWS SDK v2 직접 사용.
- Docker 이미지: 레이어 추출 후 엔트리포인트는 `org.springframework.boot.loader.launch.JarLauncher` (구 `java -jar` 대체).
- **Spring 7 `HttpHeaders`** — `MultiValueMap` 미구현. `containsKey→containsHeader`, `keySet→headerNames()`, `forEach/entrySet` 제거. 헤더 다루는 코드 주의.
- **Boot 4 모듈 분리** — `org.springframework.boot.http.client.*` 는 starter-web 컴파일 경로에 없을 수 있음 → RestClient 타임아웃은 spring-web `SimpleClientHttpRequestFactory` 로. `RestTemplateBuilder` 는 `org.springframework.boot.restclient` 모듈.

## 6. 추천 후보 (미적용 — 필요 시 도입)
| 항목 | 종류 | 용도 | 판단 |
|---|---|---|---|
| `com.github.ben-manes.versions` | 플러그인 | 구버전 의존성 탐지(`dependencyUpdates`) | 권장 |
| `nl.littlerobots.version-catalog-update` | 플러그인 | 위 결과로 카탈로그 반자동 갱신 | 선택(짝꿍) |
| `com.gorylenko.gradle-git-properties` | 플러그인 | `/actuator/info` 에 git commit 노출(배포 추적) | 권장 |
| `com.github.jk1.dependency-license-report` | 플러그인 | OSS 라이선스 목록(SI 납품 산출물) | SI 권장 |
| Boot 내장 SBOM(`/actuator/sbom`) | 기능 | 공급망 보안 SBOM | 권장(별도 플러그인보다 우선) |
| `org.gradle.test-retry` | 플러그인 | CI flaky 테스트 재시도 | 선택 |
| Spring REST Docs | 라이브러리 | 테스트 기반 API 문서 | 선택(공공 산출물) |
| WireMock | 라이브러리 | 서비스 간 HTTP 연동 테스트 목 | 서비스 간 통신 도입 시 |
| Awaitility | 라이브러리 | 비동기/가상스레드 테스트 검증 | 선택 |
| ArchUnit | 라이브러리 | 모듈/레이어 의존 규칙 강제 | 재사용 프레임워크라 권장 |
| Error Prone + NullAway | 플러그인 | 컴파일타임 버그/NPE 탐지 | 신규 모듈부터 점진(초기 노이즈 많음) |

## 7. 버전 확인 / 업데이트 방법
- 의존성 최신 여부: `./gradlew dependencyUpdates` (ben-manes 플러그인 도입 후)
- 플러그인 최신: https://plugins.gradle.org/ 에서 id 검색
- 라이브러리 최신: https://central.sonatype.com/ 또는 https://mvnrepository.com/
- 취약점: `./gradlew dependencyCheckAggregate` → `build/reports/dependency-check-report.html`
- Boot BOM 이 관리하는 버전 확인: `./gradlew :services:user-service:dependencies` 로 실제 해소 버전 확인
