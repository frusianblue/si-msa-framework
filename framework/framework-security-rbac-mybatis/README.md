# framework-security-rbac-mybatis

RBAC 영속의 **MyBatis 어댑터**. `framework-security`(코어)의 **RBAC 포트(SPI)** 를 MyBatis 로 구현한다.

## 왜 별도 모듈인가 (보안-영속 결합 분리)

`framework-security` 코어가 강제해야 하는 건 **인증(`Authenticator`)** 하나뿐이다. 그런데 과거엔 코어가
`framework-mybatis` 를 무조건 전이 의존하고 RBAC 매퍼 빈을 무조건 로딩해서, **인증만 쓰는 서비스도 MyBatis +
DataSource 를 강제로 떠안았다**(`RBAC(선택) → MyBatis(특정 기술) → DataSource(인프라)` 줄결합).

이 모듈은 RBAC 영속을 **포트/어댑터(SPI)** 로 분리한 결과다 — `Authenticator` 와 동일 사상. 코어는 포트만 알고,
MyBatis 구현은 이 어댑터에 갇힌다.

| 케이스 | 의존 | DataSource |
|--------|------|-----------|
| 인증만 (데모/위임 서비스) | 어댑터 의존 **0개** | **불필요** |
| RBAC + MyBatis (user/admin/auth-server) | `implementation project(':framework:framework-security-rbac-mybatis')` **한 줄** | 필요(기존과 동일) |
| RBAC + JPA (미래) | `-rbac-jpa` 어댑터만 새로 작성 | 구현에 따름 |

## 켜는 법

**1단 · 모듈 등록** — `settings.gradle`
```gradle
include 'framework:framework-security-rbac-mybatis'
```
프로젝트 `build.gradle`
```gradle
dependencies { implementation project(':framework:framework-security-rbac-mybatis') }
```

**2·3단 · 기능** — 코어 토글을 그대로 쓴다(`application.yml`)
```yaml
framework:
  security:
    dynamic-authorization: true   # DB 동적 인가(RBAC) — 이 어댑터가 URL-역할 매핑을 공급
    menu: true                    # 메뉴 API — 이 어댑터가 역할별 메뉴를 공급
```

> **fail-fast**: `dynamic-authorization=true`(기본) 인데 이 어댑터(= `ResourceMetadataProvider` 빈)가 없으면
> **부팅이 실패**한다. 동적 인가가 조용히 무력화(매핑 0건 → 인증만 되면 전부 허용)되는 운영 사고를 막기 위함이다.
> 동적 인가를 안 쓰면 `dynamic-authorization: false` 로 명시 → 어댑터/DataSource/MyBatis 없이 인증만으로 부팅.

## 무엇을 제공하나

| 빈 | 포트(코어) | 역할 |
|----|-----------|------|
| `MyBatisResourceMetadataProvider` | `ResourceMetadataProvider` | URL-역할 매핑 조회(동적 인가) |
| `MyBatisMenuProvider` | `MenuProvider` | 역할별 메뉴 조회 |
| `SecurityContextCurrentUserProvider`(`@Primary`) | `CurrentUserProvider`(framework-mybatis) | 감사필드(created_by/updated_by) 공급 — 코어에서 이전된 감사 브리지 |
| `SecurityMapper`(`@Mapper`) + `SecurityMapper.xml` | — | 위 provider 들이 쓰는 MyBatis 매퍼(FQN `com.company.framework.security.rbac.mapper.SecurityMapper` 유지) |

> `SecurityMapper` 의 FQN/네임스페이스는 코어 시절 그대로 유지한다. 따라서 `findRolesByLoginId` 를 직접 쓰는
> 프로젝트 코드(예: user-service `DbAuthenticationProvider`)는 **의존 한 줄만 추가하면 import 가 그대로 해소돼 코드 무변경**.

## 로딩/격리 (PITFALLS 직결)

- 어댑터 자동설정은 `@AutoConfiguration(before = SecurityAutoConfiguration.class)` — 포트 빈을 코어보다 먼저 등록해
  코어의 `@ConditionalOnBean`(RBAC 빈 활성)·`@ConditionalOnMissingBean`(fail-fast) 판정이 정확히 동작하게 한다.
- 매퍼/Provider 빈은 `@ConditionalOnClass(SqlSessionFactory.class)` 가드된 nested `@Configuration` 안에서만 만든다
  (`@ConditionalOnMissingBean` introspection 이 부재 의존 타입을 건드리지 않게 격리).
- `@MapperScan` 은 `annotationClass = Mapper.class` 필터와 함께(같은 패키지의 SPI/도메인 인터페이스 오스캔 방지 →
  `ConflictingBeanDefinitionException` 차단).

## 끄는 법

의존성 미포함 + `dynamic-authorization: false`(+ `menu: false`). 이러면 코어는 RBAC 빈을 만들지 않고 보안 체인은
`authenticated()` 로만 동작한다 — **DataSource/MyBatis 없이 부팅**.

## 덮어쓰기(프로젝트 커스텀)

`ResourceMetadataProvider`/`MenuProvider` 빈을 직접 등록하면 어댑터 기본 구현이 양보한다(`@ConditionalOnMissingBean`).
다른 영속 기술(JPA/JDBC)은 같은 포트를 구현하는 어댑터를 새로 작성하면 된다.
