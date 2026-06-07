# AUTH_T1_VERIFY.md — T1(세션 기반 인증) 검증 가이드 (재실행용)

> **목적**: T1(서버 세션 + 쿠키) 인증 흐름을 **언제든 다시 검증**할 수 있도록 방법·기대값·해석을 한 곳에 박아 둔다.
> 카탈로그(`examples/auth-types`, :8080)와 실서비스(`services/auth-session-service`, :8081) **양쪽 동일 절차**.
> 진행/결정 추적은 [`AUTH_SUMMARY.md`](./AUTH_SUMMARY.md), 개념은 [`AUTH_TYPES_REFERENCE.md`](./AUTH_TYPES_REFERENCE.md).
> 이 문서는 이후 T2~T4 검증 가이드의 **템플릿**으로도 쓴다(엔드포인트/기대 authType 만 교체).

---

## 0. 한눈에

| 대상 | 실행 태스크 | 포트 | 프로파일 |
|------|------------|------|----------|
| 카탈로그 | `:examples:auth-types:bootRun --args='--spring.profiles.active=t1-form-session'` | 8080 | `t1-form-session` |
| 실서비스 | `:services:auth-session-service:bootRun` | 8081 | `local`(자동) |

데모 계정: `alice` / `Password1!` (USER), `admin` / `Admin1234!` (ADMIN, USER).

검증 성공 기준 한 줄: **1·5단계 = `401`, 2·3·4단계 = `200`, 3단계 바디에 `principal:"alice"`**.

---

## 1. 준비

`bootRun` 은 터미널을 점유한다(앱이 떠 있는 동안 그 창은 막힘). 따라서:

1. **터미널 A** — repo 루트에서 앱 기동(위 표의 태스크). `Started ...Application in N seconds` 가 보이면 준비 완료.
2. **터미널 B** — 여기서 아래 curl 을 친다. (WSL/Git-Bash 모두 curl 기본 제공.)

> 포트만 다르고 절차는 동일하다. 아래 블록 맨 위 `BASE` 한 줄만 바꿔 양쪽에 재사용한다.

---

## 2. 한 번에 붙여넣기 (검증 스크립트)

```bash
# ── 대상 선택: 카탈로그면 8080, 실서비스면 8081 ──
BASE=http://localhost:8080      # 실서비스 검증 시: BASE=http://localhost:8081
rm -f cookies.txt

echo "── 1) 미인증 접근 (기대: 401)"
curl -i -s "$BASE/api/resource" | head -1

echo "── 2) 세션 로그인 (기대: 200 + Set-Cookie)"
curl -i -s -c cookies.txt -X POST "$BASE/api/v1/auth/session/login" \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"alice","password":"Password1!"}' \
  | grep -iE '^HTTP|^set-cookie|로그인'

echo "── 3) 쿠키로 보호 리소스 (기대: 200 + principal=alice)"
curl -s -b cookies.txt "$BASE/api/resource"; echo

echo "── 4) 로그아웃 (기대: 200, 서버 세션 무효화)"
curl -i -s -b cookies.txt -X POST "$BASE/api/v1/auth/session/logout" \
  | grep -iE '^HTTP|로그아웃'

echo "── 5) 무효화된 쿠키로 재접근 (기대: 다시 401)  ← T1 핵심 체감"
curl -i -s -b cookies.txt "$BASE/api/resource" | head -1
```

`admin` 으로도 한 번 돌려 보면(2단계 body 의 `loginId/password` 교체) 3단계 `authorities` 에 `ROLE_ADMIN` 이 더 보인다.

---

## 3. 기대 출력 (실측 기준, alice)

```
── 1) 미인증 접근 (기대: 401)
HTTP/1.1 401
── 2) 세션 로그인 (기대: 200 + Set-Cookie)
HTTP/1.1 200
Set-Cookie: JSESSIONID=...; Path=/; HttpOnly
{"success":true,"code":"OK","message":"로그인 성공","data":{"userId":"alice","name":"앨리스","roles":["USER"]},"timestamp":"..."}
── 3) 쿠키로 보호 리소스 (기대: 200 + principal=alice)
{"success":true,"code":"OK","message":"ok","data":{"message":"보호된 리소스 접근 성공","principal":"alice","authorities":["ROLE_USER"],"authType":"UsernamePasswordAuthenticationToken"},"timestamp":"..."}
── 4) 로그아웃 (기대: 200, 서버 세션 무효화)
HTTP/1.1 200
{"success":true,"code":"OK","message":"로그아웃 되었습니다.","timestamp":"..."}
── 5) 무효화된 쿠키로 재접근 (기대: 다시 401)  ← T1 핵심 체감
HTTP/1.1 401
```

> ⚠️ 쿠키 이름이 `JSESSIONID` 인 것은 정상이다(톰캣 기본). `framework.security.session.cookie-name=SESSION` 은
> 현재 실제 쿠키 이름을 바꾸지 않는다(AUTH_SUMMARY §6 참고, (a) 배선 예정). 검증엔 무영향 — curl 쿠키 jar 는 이름과 무관하게 캡처한다.

---

## 4. 각 단계가 증명하는 것

| 단계 | 요청 | 기대 | 의미 |
|------|------|------|------|
| 1 | `GET /api/resource` (쿠키 없음) | `401` | 보호 리소스가 실제로 잠겨 있음(`anyRequest().authenticated()`) |
| 2 | `POST .../session/login` + `-c` | `200` + `Set-Cookie` | **토큰이 아니라 서버 세션** 수립. 바디에 `userId/name/roles` |
| 3 | `GET /api/resource` + `-b` | `200` + `principal:"alice"` | 쿠키만으로 신원 식별. 바디에 `authType`·`authorities` |
| 4 | `POST .../session/logout` + `-b` | `200` | `session.invalidate()` — 서버가 세션을 즉시 버림 |
| 5 | `GET /api/resource` (옛 쿠키) | `401` | **로그아웃 = 즉시 무효**. JWT(만료 전까지 유효)와의 본질적 대비점 |

---

## 5. 동작 원리 (왜 이렇게 되는가)

- **엔드포인트**: 세션 모드(`framework.security.session.mode=session`)일 때만 `SessionAuthController` 가 등록된다
  (`/api/v1/auth/session/login|logout`). JWT 경로 `AuthController`(`/api/v1/auth/login`)도 동시에 뜨지만 경로가 달라 충돌하지 않는다 — T1 은 세션 경로만 쓴다.
- **인가**: 데모는 `dynamic-authorization=false` 라 보호 체인이 `anyRequest().authenticated()` 로 동작한다("인증만 되면 통과").
  `/actuator/**`, `/api/*/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**` 는 `permitAll`.
- **CSRF**: 세션 모드에서 CSRF 는 기본 on 이지만 `ignoringRequestMatchers("/api/*/auth/**")` 로 **로그인·로그아웃 둘 다 면제**된다
  (`/api/v1/auth/session/login|logout` 이 그 패턴에 매칭). 그래서 curl 만으로 XSRF 토큰 없이 흐름이 돈다.
  → 만약 **다른** POST 보호 엔드포인트를 추가하면 그때는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 동봉해야 한다.
- **쿠키 jar**: `-c cookies.txt`(쓰기) → `-b cookies.txt`(읽기). 2단계에서 받은 세션 쿠키를 3~5단계가 재사용한다. `cat cookies.txt` 로 직접 볼 수 있다.
- **로그아웃 후 401**: 5단계는 이미 무효화된 옛 쿠키를 그대로 보내므로 서버가 거부한다. 이것이 세션 방식의 "즉시 폐기" 특성이다.

---

## 6. 트러블슈팅

| 증상 | 원인 / 대응 |
|------|-------------|
| 2단계가 `403` | CSRF 면제 패턴 밖의 경로로 쳤을 가능성 — 경로가 정확히 `/api/v1/auth/session/login` 인지 확인. |
| 2단계가 `401`/`400` | 계정·비밀번호 오타(`alice`/`Password1!`) 또는 `Content-Type: application/json` 누락. |
| 3단계가 `401` | 2단계에서 `-c cookies.txt` 로 쿠키를 저장했는지, 3단계가 `-b cookies.txt` 로 읽는지 확인. |
| 5단계가 `200` | 로그아웃이 세션을 무효화하지 못함 — 4단계 응답이 `200` 인지, 같은 `cookies.txt` 를 쓰는지 확인. |
| 부팅 로그에 `Table "RESOURCES" not found` WARN | 무해(빈 캐시로 시작). H2 `INIT=RUNSCRIPT` 빈 스키마로 이미 억제됨(AUTH_SUMMARY §6). 재발 시 기능엔 영향 없음. |
| `Connection refused` | 터미널 A 의 앱이 아직 안 떴거나(`Started ...` 미출력) 포트(8080/8081)가 `BASE` 와 불일치. |

---

## 7. 8081(실서비스) 검증 시 차이

절차·기대값 **완전히 동일**. `BASE=http://localhost:8081` 로만 바꾼다. 실서비스는 `local` 프로파일이 자동(H2),
`dev`/`prod` 는 PostgreSQL 이며 그쪽은 실제 rbac 스키마를 앱 마이그레이션으로 공급한다(H2 `INIT` 스크립트와 무관).

> 멀티팟(replicas≥2) 세션 외부화 체감은 별도 단계다 — `auth-session-service` 에 `framework-session`(Spring Session Redis)을
> 추가해 띄우고, 외부화 on/off 로 "파드 전환 시 로그아웃" 차이를 확인한다(AUTH_SUMMARY §7 트랙 진행).
