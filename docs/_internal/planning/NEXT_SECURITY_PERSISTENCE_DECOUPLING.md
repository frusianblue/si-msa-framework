# NEXT_SECURITY_PERSISTENCE_DECOUPLING.md

> **상태: 활성(ACTIVE) · 코드 작성 완료(2026-06-07, 미빌드 검증)** — **§6-1~§6-7 코드/문서 작성 완료**(spring-jdbc 결합까지 분리 → 인증 전용 데모 H2/DataSource 제거 포함). **잔여: 받는 쪽(Chae) 로컬 빌드/테스트 검증만**(Maven Central 차단 → 작성 환경 빌드 불가). 빌드 그린 확인 시 `docs/archive/` 로 이동(ARCHIVED 배너).
> 관련: `docs/_internal/AUTH_SUMMARY.md` §6(트랙 Pitfalls), `docs/guide/AUTH_COMPOSITION_GUIDE.md`,
> `docs/guide/PITFALLS.md`(§ ConditionalOnMissingBean introspection / MapperScan annotationClass).

---

## 0. 한 줄 요약

`framework-security` 가 `framework-mybatis` 를 **무조건 전이 의존**하고 RBAC 매퍼 빈을 **무조건 로딩**하는 탓에,
**인증만 쓰는 서비스도 MyBatis + DataSource 를 강제로 떠안는다.** RBAC 영속을 **포트/어댑터(SPI)** 로 분리해
보안 코어가 특정 영속 기술(MyBatis)·인프라(DataSource)에 결합되지 않게 한다.

---

## 1. 문제 (현재 상태, 실측)

의존 사슬:
```
framework-security  ──api project(':framework:framework-mybatis')──>  mybatis-spring-boot-starter + spring-boot-starter-jdbc
        │  (framework-security/build.gradle 에 박혀 있음 — 무조건)
        ▼
SecurityAutoConfiguration (무조건 로딩):
  @MapperScan("com.company.framework.security.rbac.mapper")        ← SqlSessionFactory 필요
  @Bean securityMetadataService(SecurityMapper)                     ← DataSource 필요
  @Bean dynamicAuthorizationManager(SecurityMetadataService)
  @Bean menuService(SecurityMapper)         (menu=true 일 때)
  @Bean menuController(MenuService)         (menu=true 일 때)
```

결과: 보안을 쓰면 **DataSource 가 하나는 있어야 부팅**(SqlSessionFactory 가 못 뜨면 컨텍스트 실패).
→ 인증만 쓰는 데모/위임 서비스(`auth-session-service` 등)가 의미 없는 H2 를 끼워야 했음.

**세 겹 결합**(틀린 부분): 보안이 강제해야 하는 건 **인증(`Authenticator`)** 하나뿐인데,
RBAC(선택 기능) → MyBatis(특정 기술) → DataSource(인프라) 가 줄줄이 강제됨.

---

## 2. 결정

- **(가) 별도 어댑터 모듈 신설**: `framework-security-rbac-mybatis`.
- 보안 코어는 **RBAC 포트(SPI)** 만 알고, MyBatis 구현은 어댑터로 분리(= `Authenticator` 와 동일 사상).
- `framework-security` 의 `api project(':framework:framework-mybatis')` **제거**.
- RBAC 쓰는 서비스는 어댑터 의존 **한 줄** 추가. 인증만 쓰는 서비스는 **의존 0개 추가, DataSource 불필요.**

미래 확장: `framework-security-rbac-jpa`, `framework-security-rbac-jdbc` 등 어댑터만 새로 작성.

---

## 2.5 설계 확정 보강 (2026-06-07 소스 실측 반영 · LOCKED)

> 착수 전 실제 소스 대조로 계획의 **두 갭**을 발견·해소했다. 다음 세션은 이 절을 그대로 실행한다.

### 발견 1 — 두 번째 mybatis 결합(감사 브리지)
RBAC 외에 `SecurityAutoConfiguration`(line 3·157)이 `SecurityContextCurrentUserProvider`
(= framework-mybatis 의 `CurrentUserProvider` SPI 구현, created_by/updated_by 공급)를 `@Primary` 빈으로 등록한다 —
코어 → mybatis 의 **두 번째** 컴파일 결합. §6-2 "mybatis 의존 제거" + §6-5 ArchUnit "코어 no-mybatis" 를 그대로 적용하면 이 브리지가 컴파일 불가.
- **결정**: `SecurityContextCurrentUserProvider` 도 **어댑터로 이전**(security+mybatis 둘 다 가진 유일한 자리). 어댑터에서 `@Primary CurrentUserProvider` 로 등록.
- **고아 의존 0 증명**: framework-mybatis 를 쓰는 서비스 3종(user/admin/auth-server)이 **전부 `dynamic-authorization=true`(기본값)** → 어차피 어댑터를 물게 됨.
  보안-only 서비스(`auth-session-service`)는 mybatis 자체가 없어 감사 대상 엔티티가 없으므로 무영향.
- 실측: framework-mybatis `MyBatisConfig.defaultCurrentUserProvider` 는 `@ConditionalOnMissingBean(CurrentUserProvider.class)` 라 어댑터 빈이 우선.
  주입 순서 견고성 위해 어댑터 빈에 `@Primary` 유지(현행 동작과 동일).

### 발견 2 — auth-server 도 마이그레이션 대상
계획 §6-4 는 user/admin 만 적었으나, **auth-server** 도 `dynamic-authorization` 미지정 = 기본값 `true`(`FrameworkSecurityProperties.dynamicAuthorization=true`) +
`V3__rbac_metadata.sql` 보유 → fail-fast 에 걸린다. **셋(user/admin/auth-server) 모두** 같은 delivery 에 어댑터 한 줄 추가
(안 하면 부팅 실패 — 의도된 fail-fast, 회귀 방지 위해 동시 적용).

### 결정 — SecurityMapper FQN 유지
`SecurityMapper`(+`SecurityMapper.xml` 네임스페이스)를 어댑터로 옮기되 **패키지/FQN 그대로**
(`com.company.framework.security.rbac.mapper.SecurityMapper`) 유지. user-service `DbAuthenticationProvider` 의 `import` 가 어댑터 jar 에서 그대로 해소 →
**user-service 코드 무변경**(의존 한 줄만 추가). 코어엔 이제 이 패키지가 없고 어댑터가 소유(classpath 분할 패키지, 런타임 정상).

### fail-fast 동작 확정
`dynamic-authorization=true` ∧ `ResourceMetadataProvider` 부재 → **프로파일 무관 부팅 실패**(조용한 인가 무력화 차단).
`JwtSecretSafetyGuard`(InitializingBean#afterPropertiesSet 에서 throw) 패턴 재사용.

---

## 3. SPI(포트) 설계

DB 를 만지는 지점은 `SecurityMapper`(rbac.mapper) 3개 메서드:
`findAllResources()`(동적 인가용 URL-역할), `findMenusByRoles(roles)`(메뉴), `findRolesByLoginId(loginId)`(역할).

> 주의(실측): `findRolesByLoginId` 는 **프로젝트의 Authenticator 가 직접 사용**한다
> (예: `services/user-service` 의 `DbAuthenticationProvider`). 프레임워크 로그인 흐름은 역할을
> `AuthenticatedUser.roles()` 로 받으므로, role 조회는 **프로젝트/어댑터 관심사**다.

### 보안 코어(`framework-security`)에 둘 포트
```java
// rbac.spi (신설 패키지)
public interface ResourceMetadataProvider {     // 동적 인가
    List<Resource> findAllResources();
}
public interface MenuProvider {                  // 메뉴 API
    List<Menu> findMenusByRoles(List<String> roles);
}
```
- 도메인 타입 `Resource` / `Menu` / `MenuDto` 는 **포트 계약**이므로 보안 코어에 **잔류**.
- `SecurityMetadataService` / `DynamicAuthorizationManager` / `MenuService` / `MenuController` 는
  `SecurityMapper`(MyBatis) 대신 위 **포트**에 의존하도록 변경(코어에 잔류).

### 어댑터(`framework-security-rbac-mybatis`)로 이전
- `SecurityMapper`(@Mapper 인터페이스) + `resources/mapper/security/SecurityMapper.xml`
- `MyBatisResourceMetadataProvider implements ResourceMetadataProvider`
- `MyBatisMenuProvider implements MenuProvider`
- **`SecurityContextCurrentUserProvider`(코어에서 이전) — `@Primary CurrentUserProvider`(감사 브리지, §2.5 발견 1)**
- (선택) `findRolesByLoginId` 를 제공할 `MyBatisRoleProvider` 또는 `SecurityMapper` 자체를 어댑터가 노출
  → user-service 의 `DbAuthenticationProvider` 가 이걸 주입받게.

---

## 4. 로딩/격리 설계 (PITFALLS 직결 — 반드시 준수)

1. **어댑터 자동설정은 `@ConditionalOnClass(SqlSessionFactory.class)` 가드된 nested static `@Configuration` 안에서만**
   매퍼/Provider 빈을 만든다. (PITFALLS: `@ConditionalOnMissingBean` 타입 미명시 시 형제 빈 introspection →
   부재 의존 클래스 로딩. nested + ConditionalOnClass 로 격리.)
2. **`@MapperScan` 은 어댑터 안에서 `annotationClass = Mapper.class` 필터와 함께**. (PITFALLS: 필터 없으면 SPI 인터페이스까지 스캔 → `ConflictingBeanDefinitionException`.)
3. 코어 `SecurityAutoConfiguration` 에서 `@MapperScan` 및 `SecurityMapper` 직접 참조 **전부 제거**.
4. 코어의 RBAC 빈은 포트 빈 존재를 조건으로:
   - `dynamicAuthorizationManager` → `@ConditionalOnBean(ResourceMetadataProvider.class)`
   - `menuService`/`menuController` → `@ConditionalOnBean(MenuProvider.class)` (+ 기존 `menu=true`)

### fail-fast (조용한 인가 무력화 방지)
- `dynamic-authorization=true` **인데** `ResourceMetadataProvider` 빈이 없으면 → **부팅 실패**.
  메시지: "dynamic-authorization=true 이면 RBAC provider 가 필요합니다 — `framework-security-rbac-mybatis`(또는 다른 RBAC 어댑터)를 의존에 추가하세요."
  (`@ConditionalOnProperty(dynamic-authorization=true)` + `@ConditionalOnMissingBean(ResourceMetadataProvider.class)` 가드 빈으로 fail-fast 구현 — DevAuthSafetyGuard/JwtSecretSafetyGuard 패턴 재사용.)
- `dynamic-authorization=false` → 포트 불필요, 보안 체인은 `authenticated()` 로만. **DataSource·MyBatis 없이 부팅.**

---

## 5. "그냥 MyBatis 쓰는 경우" — 변화는 의존성 한 줄

| 케이스 | 현재 | 개선 후 |
|--------|------|---------|
| 인증만 (데모/위임 서비스) | MyBatis+DataSource 강제 | 의존 0개 추가, DataSource 불필요 |
| RBAC + MyBatis (user/admin-service) | 자동 | `implementation project(':framework:framework-security-rbac-mybatis')` **한 줄** |
| RBAC + JPA (미래) | 불가 | `-rbac-jpa` 어댑터만 |

동작은 100% 동일(SecurityMapper/XML/동적 인가/메뉴 그대로, 어댑터가 통째로 담음).

---

## 6. 작업 체크리스트

### 6-1. 새 모듈 신설 (`framework-security-rbac-mybatis`)
- [x] `framework/framework-security-rbac-mybatis/` 생성(build.gradle: `api project(':framework:framework-security')` + `api project(':framework:framework-mybatis')`)
- [x] `SecurityMapper` + `SecurityMapper.xml` 이전(**FQN/네임스페이스 유지** — §2.5), `MyBatisResourceMetadataProvider`/`MyBatisMenuProvider` 작성
- [x] `SecurityContextCurrentUserProvider` 코어→어댑터 이전 + `@Primary CurrentUserProvider` 빈 등록(감사 브리지, §2.5 발견 1)
- [x] 어댑터 `@AutoConfiguration`(nested `@ConditionalOnClass(SqlSessionFactory.class)` + `@MapperScan(annotationClass=Mapper.class)`)
- [x] `META-INF/.../AutoConfiguration.imports` 등록

### 6-2. 코어 변경 (`framework-security`)
- [x] `build.gradle`: `api project(':framework:framework-mybatis')` **제거**
  - ⚠️ **착수 시 발견(계획 갭) → 해소**: `spring-jdbc`(JdbcTemplate)가 framework-security 로 **mybatis 전이로만** 들어왔다 — framework-security 자체의 JDBC 저장소(`JdbcTokenStore`/`JdbcPasswordHistoryStore`/`JdbcConcurrentSessionService`)가 `JdbcTemplate` 을 직접 쓰므로 mybatis 제거 시 컴파일 깨짐. → **`compileOnly 'spring-boot-starter-jdbc'`** 로 직접 선언 + `JdbcTemplate` @Bean 들을 nested `@ConditionalOnClass(JdbcTemplate)`(`SecurityAutoConfiguration.JdbcSecurityStoreConfig`, `TokenStoreAutoConfiguration.JdbcTokenStoreConfig`)로 격리. 이로써 jdbc 백엔드(token-store/…=jdbc)를 쓰는 host 만 starter-jdbc 를 제공하고, **인증만 쓰는 서비스는 DataSource 완전 불필요**(§6-7 H2 제거 가능). framework-lock/audit/mfa/… 가 이미 쓰는 compileOnly 패턴과 동일 → 기존 consumer 영향 0(각자 starter-jdbc 보유).
  - ⚠️ **빌드 검증서 발견(2): `api project(framework-mybatis)` 제거가 전이 consumer 를 깸** — `framework-mfa` 가 `CurrentUserProvider`(`com.company.framework.mybatis.support`)를 **security→mybatis 전이로** 받아쓰고 있었다(mfa build.gradle 주석에 명시). 전이가 끊겨 `framework-mfa:compileJava` 가 `package com.company.framework.mybatis.support does not exist` 로 실패. → mfa 에 **`compileOnly project(':framework:framework-mybatis')` + `testImplementation`** 직접 선언(mfa 의 "호스트 제공·드래그인 방지" 원칙대로). 전수 점검 결과 security-의존 모듈 중 mybatis 타입을 쓰는 건 mfa 뿐(audit/oauth-client/redis/saml-sp/session/webauthn=0). **교훈: `api` 전이 의존을 끊을 땐 그 전이로 따라오던 타입(여기선 mybatis.support.CurrentUserProvider)을 쓰는 consumer 를 전부 찾아 직접 선언으로 옮겨야 한다.**
- [x] `rbac.spi` 포트 신설(`ResourceMetadataProvider`, `MenuProvider`)
- [x] `SecurityMetadataService`/`DynamicAuthorizationManager`/`MenuService`/`MenuController` 를 포트 의존으로 변경
- [x] `SecurityAutoConfiguration`: `@MapperScan`·`SecurityMapper` 참조 제거, RBAC 빈에 `@ConditionalOnBean(포트)`
- [x] `SecurityAutoConfiguration`: `CurrentUserProvider` import·`securityContextCurrentUserProvider()` @Bean 제거 + `SecurityContextCurrentUserProvider.java` 코어에서 삭제(어댑터로 이전, §2.5) → 코어 `com.company.framework.mybatis.*` 잔여 import 0
- [x] dynamic-authorization=true + 포트 부재 → fail-fast 가드 빈

### 6-3. 신규 모듈 등록(프로젝트 규약 — 동시 등록 필수)
- [x] `settings.gradle` include
- [x] `framework-archtest/build.gradle` 의존 추가
- [x] archtest `.imports` 가드 대상 추가
- [x] guard test(`getResources` 로 `.imports` 직접 검증) 추가
- [x] root `build.gradle` jacocoAggregation 추가

### 6-4. 기존 서비스 마이그레이션
- [x] `services/user-service/build.gradle` 에 `-rbac-mybatis` 의존 한 줄 추가(현재 dynamic-authorization=true·기본값)
- [x] `services/admin-service/build.gradle` 동일(기본값 true)
- [x] `services/auth-server/build.gradle` 동일(기본값 true + `V3__rbac_metadata.sql`, §2.5 발견 2)
- [x] `DbAuthenticationProvider` — `SecurityMapper` FQN 유지(§2.5)이므로 **코드 무변경**, 어댑터 의존만으로 import 해소
- [ ] 로컬 부팅·기존 동작 회귀 확인(Chae)

### 6-5. ArchUnit 회귀 방지
- [x] 규칙 추가: `framework-security` 는 `framework-mybatis`/`org.mybatis..`/`org.apache.ibatis..` 에 의존 금지
- [x] 규칙 추가: rbac.spi 포트는 MyBatis 타입을 시그니처에 노출 금지

### 6-6. 문서 동반 갱신(코드=문서 동시)
- [x] `framework-security/README.md`(켜는법/쓰는법/끄는법 + RBAC 어댑터 분리 설명 + `## 실전 사용 예 (코드)`)
- [x] `framework-security-rbac-mybatis/README.md`(신규, `## 실전 사용 예 (코드)` 포함)
- [x] root `README.md`, `docs/FRAMEWORK_MODULES.md`(36→37 모듈, 카테고리 반영)
- [x] `docs/guide/AUTH_COMPOSITION_GUIDE.md`, `MODULE_COMPOSITION`
- [x] `docs/guide/PITFALLS.md`(보안-영속 결합 분리 교훈 추가)
- [x] `framework/README.md` 모듈 링크

### 6-7. T1 데모 후속(이 리팩터 완료 후)
> ✅ **선행(spring-jdbc 분리) 완료**: framework-security 의 `spring-jdbc` 를 `compileOnly` 로 돌리고 `JdbcTemplate` @Bean 들을
> nested `@ConditionalOnClass(JdbcTemplate)`(`JdbcSecurityStoreConfig`/`JdbcTokenStoreConfig`)로 가드 → 인증 전용 서비스는 spring-jdbc 부재로
> DataSourceAutoConfiguration 이 백오프 = DataSource 불필요. (다른 jdbc-using 모듈은 이미 각자 `compileOnly starter-jdbc` 선언 → 영향 0.)
- [x] `examples/auth-types`·`services/auth-session-service` 에서 **H2/DataSource 제거**(인증만 → DataSource 불필요 확인)
- [x] `AUTH_SUMMARY.md` §5/§6 갱신(부팅 전제 "DataSource 필수" 항목 해소 기록)

---

## 7. 영향 범위 / 리스크

- user-service / admin-service 는 `dynamic-authorization=true` 라 **어댑터 한 줄 추가 전까지 부팅 실패**(fail-fast 의도된 동작). 마이그레이션을 같은 PR/delivery 에 포함해 회귀 없게.
- 빌드 환경 제약: Maven Central 차단 → Gradle 실행 불가. Claude 정적 작성, Chae 로컬 빌드/테스트.
- 검증: 포트 분리 로직은 순수 JDK 하니스로, Spring 와이어링은 Chae 로컬 위임.
