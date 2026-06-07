# auth-session-service (T1 — 서버 세션 기반 인증)

인증 방식별로 분리한 실서비스 중 **세션 방식**. 이름 그대로 `HttpSession` + 쿠키(단일 파드는 톰캣 기본 `JSESSIONID`, framework-session 추가 시 `SESSION`)로 신원을 유지한다.
(다른 방식: `auth-jwt-service` / `auth-oidc-service` / `auth-saml-service`)

> 학습·체감용 카탈로그는 `examples/auth-types`(프로파일 `t1-form-session`). 개념은 `docs/_internal/AUTH_TYPES_REFERENCE.md`.

## 인증 방식
- `framework.security.session.mode=session` — 로그인 시 서버 세션 수립, 쿠키로 식별.
- 단일 인증 계약 `Authenticator` 를 `DemoAuthenticator`(인메모리)로 구현. 실 운영은 DB/LDAP/SSO 로 교체.

데모 계정: `alice`/`Password1!`(USER), `admin`/`Admin1234!`(ADMIN, USER).

## 실행
```bash
./gradlew :services:auth-session-service:bootRun       # local, H2, :8081
```

## 검증 (curl)
```bash
curl -i localhost:8081/api/resource                      # 미인증 401
curl -i -c c.txt -X POST localhost:8081/api/v1/auth/session/login \
  -H 'Content-Type: application/json' -d '{"loginId":"alice","password":"Password1!"}'
curl -i -b c.txt localhost:8081/api/resource             # 200
curl -i -b c.txt -X POST localhost:8081/api/v1/auth/session/logout
```

## 멀티팟(K8s) 주의 — T1 핵심
replicas≥2 면 톰캣 세션이 파드마다 따로라 파드 전환 시 로그아웃처럼 보인다. 세션을 공유하려면
`build.gradle` 에서 `framework-session`(Spring Session Redis) 의존을 켜고 Redis 를 붙인다. 이것이 무상태(JWT) 서비스와의 본질적 차이다.

## 부팅 메모
- framework-security 가 framework-mybatis 를 전이로 끌어옴 → DataSource(H2/PG) 필수. local 은 H2 인메모리.
- `dynamic-authorization=false`/`menu=false` 로 rbac **인가 연결**을 끈다. 단 `SecurityMetadataService` 는 토글과 무관하게
  부팅 시 `findAllResources()` 를 1회 호출하므로, local(H2) 은 빈 rbac 스키마(행 0)를 `INIT=RUNSCRIPT FROM
  'classpath:db/rbac-empty-schema.sql'` 로 선생성해 조용히 부팅(없어도 무해하나 시끄러운 WARN). dev/prod(PG)은 실제 스키마.
