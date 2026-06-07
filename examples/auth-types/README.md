# auth-types — 인증 방식(유형) 카탈로그

인증 방식이 여러 가지임을 **트랙별 실행 가능한 프로파일**로 보여 준다. 한 앱에 여러 방식을 심고
`--spring.profiles.active=<트랙>` 으로 골라 띄워, **같은 보호 리소스(`GET /api/resource`)를 다른 방식으로 여는** 차이를 A/B 비교한다.

> 개념 카탈로그는 `docs/_internal/AUTH_TYPES_REFERENCE.md`, 진행/실행 추적은 `docs/_internal/AUTH_SUMMARY.md` 참고.
> 실전 적용 서비스는 `services/auth-<방식>-service` (방식별 분리).

---

## 트랙

| 프로파일 | 트랙 | 방식 | 상태 |
|---|---|---|---|
| `t1-form-session` | T1 | 서버 세션(HttpSession + 쿠키) | ✅ |
| `t2-jwt` | T2 | 무상태 JWT(self-issued) | 예정 |
| `t3-oidc` | T3 | OIDC RP 위임 | 예정 |
| `t4-saml` | T4 | SAML SP 위임 | 예정 |

데모 계정: `alice` / `Password1!` (USER), `admin` / `Admin1234!` (ADMIN, USER).

---

## 사전 준비

별도 준비 없음. 이 예제는 **루트 멀티프로젝트에 편입**되어 `project(':framework:..')` 를 직접 소비하므로
`publishToMavenLocal` 단계가 필요 없다(과거 standalone 빌드의 mavenLocal 소비 방식은 폐기).

---

## 실행

repo 루트에서:

```bash
./gradlew :examples:auth-types:bootRun --args='--spring.profiles.active=t1-form-session'
```

---

## T1 — 세션 기반 검증 (curl)

```bash
# 1) 보호 리소스 — 미인증 401
curl -i localhost:8080/api/resource

# 2) 세션 로그인 → 쿠키 저장 (Set-Cookie: JSESSIONID=...  ← 단일 파드는 톰캣 기본 이름. framework-session 추가 시 SESSION)
curl -i -c cookies.txt -X POST localhost:8080/api/v1/auth/session/login \
  -H 'Content-Type: application/json' -d '{"loginId":"alice","password":"Password1!"}'

# 3) 쿠키로 보호 리소스 — 200 (principal=alice, authType=UsernamePasswordAuthenticationToken)
curl -i -b cookies.txt localhost:8080/api/resource

# 4) 로그아웃 → 서버 세션 무효화
curl -i -b cookies.txt -X POST localhost:8080/api/v1/auth/session/logout

# 5) 무효화된 쿠키로 재접근 — 다시 401
curl -i -b cookies.txt localhost:8080/api/resource
```

### 체감 포인트
- 로그인 응답엔 **토큰이 없다** — 신원은 서버 세션에 있고 클라이언트는 쿠키만 들고 다닌다.
- **멀티팟**: replicas≥2 로 띄우면 세션이 파드마다 따로라 로그아웃처럼 보인다. 해결 = 세션 외부화
  (`framework-session` 의존 추가 + Redis). 이것이 T1 의 핵심 체감이며 T2(무상태)와의 본질적 대비점이다.
- 로그아웃은 **즉시** 무효(서버가 세션을 버림). T2 의 "토큰 만료 전까지 유효"와 대비.

---

## 부팅 메모
- **DataSource 불필요**(2026-06-07 보안-영속 결합 분리 이후) — framework-security 는 더 이상 `framework-mybatis`/`spring-jdbc` 를
  전이로 끌어오지 않는다. 데모는 인메모리 인증기 + `dynamic-authorization=false` 라 RBAC DB 조회가 없고 `DataSourceAutoConfiguration`
  도 비활성 → **H2/datasource 설정 없이 기동**(과거의 `rbac-empty-schema.sql`·`INIT=RUNSCRIPT` 는 제거).
- rbac DB 동적 인가는 끈 상태(`framework.security.dynamic-authorization=false`) → "인증만 되면 통과". RBAC 비교 트랙을
  추가할 땐 `framework-security-rbac-mybatis` + DB 드라이버를 그 프로파일에만 더한다.
- 세션 모드에서도 JWT `AuthController`(`/api/v1/auth/login`)가 함께 뜨지만 경로가 달라 충돌 없음 — T1 은 세션 경로만 쓴다.
