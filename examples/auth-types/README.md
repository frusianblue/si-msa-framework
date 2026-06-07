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

## 사전 준비 (1회)

이 예제는 `com.company:framework-*:1.0.0` 을 mavenLocal 에서 소비한다. repo 루트에서:

```bash
./gradlew :framework:framework-core:publishToMavenLocal \
          :framework:framework-mybatis:publishToMavenLocal \
          :framework:framework-security:publishToMavenLocal
```

---

## 실행

```bash
cd examples/auth-types
./gradlew bootRun --args='--spring.profiles.active=t1-form-session'
```

---

## T1 — 세션 기반 검증 (curl)

```bash
# 1) 보호 리소스 — 미인증 401
curl -i localhost:8080/api/resource

# 2) 세션 로그인 → 쿠키 저장 (Set-Cookie: SESSION=...)
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
- framework-security 는 framework-mybatis 를 전이로 끌어온다 → **DataSource(H2) 필수**. 데모는 H2 인메모리(빈 스키마).
- rbac DB 동적 인가는 `framework.security.dynamic-authorization=false` 로 꺼서 rbac 테이블 없이 부팅("인증만 되면 통과").
- 세션 모드에서도 JWT `AuthController`(`/api/v1/auth/login`)가 함께 뜨지만 경로가 달라 충돌 없음 — T1 은 세션 경로만 쓴다.
