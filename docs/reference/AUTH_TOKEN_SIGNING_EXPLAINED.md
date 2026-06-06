# 인증 토큰 서명 구조 해설 (AUTH_TOKEN_SIGNING_EXPLAINED)

> 목적: auth-server 의 토큰 서명·서명키 생명주기를 처음 보는 사람도 이해하도록 정리한 학습/참조 문서.
> 모든 타입·메서드 이름은 실제 소스(`master`)에서 확인한 것이다. 함정 사례 원본은 [`PITFALLS.md`](PITFALLS.md) §5.
> 작성 계기: §S3' "토큰 302 둔갑" 버그 디버깅 세션의 설명을 별도 자산으로 보존.

---

## 0. 한눈에

| 질문 | 답 |
|---|---|
| 토큰 발급 주체가 하나인가? | **아니다. 둘(이중 발급기).** 내부 직접 로그인용 자체 JWT + AS(OIDC OP)용 표준 토큰 |
| 서명에 쓰는 키는? | 내부 = HMAC 대칭 비밀(`framework.jwt.secret`), AS = RSA 비대칭 개인키(RS256) |
| RSA 서명키는 누가 주나? | 아무도 안 준다. **AS 가 스스로 생성**(부트스트랩/회전). 소비자는 공개키만 JWKS 로 받아간다 |
| AES 마스터키는 어디에? | **env(k8s Secret)** — DB 아님. DB 의 RSA 개인키를 저장 직전 암호화하는 용도 |
| RSA 서명키는 한 개만? | 아니다. **서명=최신 ACTIVE 1개, 검증=ACTIVE+RETIRED 전부**(회전 오버랩) |

---

## 1. 이중 발급기 (dual-issuer) — 내부 vs 외부

토큰을 발급하는 길이 둘이고, 다운스트림(게이트웨이/리소스서버)은 발급자 종류에 따라 재검증 경로를 가른다. 코드상 구분자는 `framework-security` 의 `TokenIssuerKind` enum 이다.

| | `INTERNAL` | `AUTHORIZATION_SERVER` |
|---|---|---|
| 발급 주체 | `framework-security` 의 `JwtProvider` | `services/auth-server` (Spring Authorization Server = OIDC OP) |
| 쓰임 | 내부 직접 로그인(id/pw → 토큰) | 외부/위임 흐름(OAuth2 · OIDC) |
| 서명 방식 | **HMAC** 대칭키(`Keys.hmacShaKeyFor(secret)`) | **RS256** RSA 비대칭 개인키 |
| `iss` 클레임 | 없음 | 있음(AS issuer) |
| 검증 방법 | 같은 대칭 비밀로 검증 | **JWKS**(공개키)로 검증 |
| 폐기(로그아웃) | `jti` 블랙리스트(중앙 로그아웃) | AS `/oauth2/revoke` 경로 — 자체 jti 블랙리스트와 혼용 금지 |
| 키 저장 | DB 저장 없음(설정/env 의 시크릿) | DB(`auth_signing_key`)에 RSA 개인키 저장 |

핵심: **내부 사용자도 JWT 를 받는다.** 단 그 JWT 는 AS 가 아니라 `JwtProvider` 가 발급한 HMAC 토큰이다. 우리가 §S3' 에서 디버깅한 "서명키 복호화" 문제는 오직 **AS(RS256) 경로**의 이야기이고, 내부 HMAC 경로에는 RSA 도 DB 키 저장도 없다.

왜 이렇게 나눴나(이중 발급기 경계): 내부 인증은 자체 발급 JWT 로 가볍게 유지하고, 외부/위임 발급만 표준 OIDC OP(AS)로 처리한다. 리소스서버는 `iss` 클레임 유무로 분기해 두 발급자를 모두 신뢰한다(`ResourceServerJwtVerifier` / `DownstreamTokenAuthenticator`).

---

## 2. AS 의 RSA 서명: 두 개의 키를 구분하라

여기서 가장 흔한 오해가 **"DB 에 잘못된 AES 값이 들어가 있다"** 인데, 정확히는 아니다. 키가 **두 종류**다.

- **RSA 서명키** — 토큰(id_token/access_token)에 RS256 서명을 거는 개인키. **DB 에 저장**된다.
- **AES 마스터키**(`AES_SECRET` / `framework.crypto.aes-secret`) — 그 RSA 개인키를 **DB 에 넣기 전에 암호화**하는 키. **env(k8s Secret)에 있고 DB 에는 없다.**

### 2.1 왜 RSA 키가 DB 에 있어야 하나

auth-server 는 k8s 에서 **여러 파드(replica)** 로 뜬다. 파드마다 제각각 다른 RSA 키를 들면 A 파드가 서명한 토큰을 B 파드 기준으로 검증 못 한다. 그래서:

1. RSA 서명키를 **DB(`auth_signing_key`) 한 곳에 모아 모든 파드가 공유**한다.
2. 키 **회전** 시 옛 키도 잠깐(grace) 검증용으로 남겨야 하므로(§3) 여러 행을 DB 로 관리한다.
3. AS 의 모든 저장은 JDBC(다중 파드 전제) — 인메모리 금지.

즉 **DB 에 들어가는 건 "RSA 개인키"** 이지 AES 가 아니다.

### 2.2 protect / reveal — AES 가 쓰이는 두 지점

RSA 개인키를 DB 에 평문으로 두면 DB 유출 = 토큰 위조 가능이라 치명적이다. 그래서 저장 직전 AES 로 암호화한다. 구현은 `AesSigningKeyCipher`(`SigningKeyCipher` 인터페이스의 1차 구현).

| 동작 | 메서드 | 내용 |
|---|---|---|
| 쓰기(저장 직전) | `protect(jwkJson)` | `encryptOnWrite=true` 면 `"enc:" + AES암호화(개인키 JWK)`. false 면 평문 그대로(토글 off) |
| 읽기(로드 직후) | `reveal(stored)` | `enc:` 마커로 시작하면 AES 복호화, 아니면 평문으로 간주(레거시/데모 호환) |

DB `jwk_json` 컬럼이 `enc:` 로 시작하면 암호문이라는 표식이다(평문 JWK 는 `{"kty":...}` 로 시작하므로 절대 `enc:` 로 시작하지 않음). KMS/Vault 백엔드로 바꾸려면 `SigningKeyCipher` 만 다시 구현하면 된다(결정: AES 로 시작, KMS 후속).

### 2.3 전체 생명주기 (서명 흐름)

```
RSA 키쌍 생성(generateRsaKey)
        │  protect()  ── AES_SECRET(env) 으로 암호화
        ▼
auth_signing_key (DB)   ── jwk_json = "enc:..." (암호문)
        │  loadFromDb() → reveal() ── AES_SECRET 으로 복호화
        ▼
JWKSet 구성 (복호화된 RSA 들)
        │
        ▼
토큰 RS256 서명 (최신 ACTIVE 키)
        │
        ▼
소비자(게이트웨이/리소스서버)는 /oauth2/jwks 의 공개키로 검증
```

관련 클래스: 생성 `RsaSigningKeyGenerator`/`generateRsaKey()`, 보호 `AesSigningKeyCipher`, 저장/조회 `SigningKeyMapper`, JWK 소스 `JdbcRotatingJwkSource`(SAS 의 `JWKSource` 구현), 회전 `SigningKeyRotationService`.

---

## 3. 서명키는 누가 주나 + 회전(여러 개)

### 3.1 키의 출처 — AS 가 스스로 만든다

표준 OIDC 에서 **OP(=AS)가 자기 토큰의 신뢰 뿌리(root of trust)** 다. 누가 외부에서 서명키를 "주는" 게 아니라, AS 가 **직접 RSA 키쌍을 생성**한다.

- 첫 부팅 시: `JdbcRotatingJwkSource` 생성자가 `ensureBootstrapKey()` 를 호출해 사용 가능한 ACTIVE 키가 없으면 1개를 부트스트랩한다.
- 이후: 회전 스케줄러가 주기적으로 새 키를 생성한다.
- 소비자는 **공개키만** `/oauth2/jwks` 로 받아간다. 개인키는 AS 밖으로 나가지 않는다.

"지금은 그냥 입력한 거 아니냐" → 수동 입력이 아니라 **첫 부팅에 자동 생성**된 것이다. 이게 정상·표준 모델이다. 운영에서 더 엄격히 하려면 KMS/Vault/HSM 에서 키를 주입하도록 `SigningKeyCipher` 를 교체할 수 있다(인터페이스가 그 교체 지점).

(대조) 내부 HMAC 경로의 "키"는 비대칭 키쌍이 아니라 **대칭 공유 비밀**이라 `JwtProperties.secret()`(=`framework.jwt.secret` 등 설정/env)에서 주입받는다. 발급자와 검증자가 같은 비밀을 안다.

### 3.2 한 개가 아니다 — ACTIVE / RETIRED 회전

`SigningKeyRotationService.rotateOnce()` 가 **한 트랜잭션**에서:

1. 직전 ACTIVE 전부 RETIRE(`retireAllActive`, `retired_at=now` 기록)
2. 새 ACTIVE INSERT
3. grace 지난 RETIRED 삭제(`deleteRetiredOlderThan`)

| 용도 | 사용하는 키 | 매퍼 |
|---|---|---|
| **서명**(발급) | 최신 ACTIVE **1개** | `findNewestActive()` |
| **검증**(JWKS 공개) | ACTIVE + RETIRED **전부** | `findAllUsable()` |

그래서 회전 직후에도 **옛 키로 서명된 토큰이 grace 동안 검증**된다. 정상 상태 = ACTIVE 1 + RETIRED N.

설계 주의점(코드 주석에 박제됨):
- **순서가 "RETIRE 먼저 → INSERT"** 인 이유: 독자(JWKS)는 트랜잭션 커밋 전/후만 보므로 0-ACTIVE 중간상태가 노출되지 않고, 락이 빠진 경합 최악도 ACTIVE 2개(오버랩이 흡수)라 **서명 불가(0-ACTIVE)로는 절대 안 떨어진다**. (INSERT 먼저 순서는 두 파드가 서로의 새 키를 RETIRE 해 0-ACTIVE 위험.)
- **grace 정리 기준 = `retired_at`**(폐기 시각)이지 `created_at`(생성 시각)이 아니다. 생성 시각 기준이면 grace < 회전주기일 때 직전 키가 RETIRE 즉시 삭제돼 오버랩이 깨진다.
- **grace ≥ (access 토큰 최대 수명 + JWKS 캐시 TTL + 여유)** 여야 회전 직후 발급 토큰이 오버랩 안에서 검증된다.
- 다중 파드 중복 회전 1차 방지는 `@SchedulerLock`(리더 선출, `SigningKeyRotationScheduler`). 운영 다중 파드는 `framework.lock.enabled=true` + `type=jdbc|redis` 가 필수(memory 타입은 파드 간 상호배제 불가).

---

## 4. §S3' 사례: "토큰이 302 로 둔갑한" 버그

### 4.1 무엇이 실제로 잘못됐나

- 어느 시점에 `AES_SECRET`(env)을 교체했다.
- 그런데 DB 는 PVC(영속 볼륨)라 **옛 AES 로 암호화된 RSA 키 행이 그대로 생존**했다.
- 이제 `reveal()` 이 옛 암호문을 현재 AES 로 풀려다 **복호화 실패** → `loadFromDb()` 가 그 키를 건너뜀 → 쓸 수 있는 키 0개 → `IllegalStateException`.

→ 정정: **"DB 에 잘못된 AES 값이 들어간" 게 아니라**, DB 의 RSA 키가 **옛 AES 로 암호화된 채 남아 현재 AES 로 복호화 안 됐던** 것이다.

여기에 2차 버그가 겹쳤다. 옛 `ensureBootstrapKey()` 는 **"ACTIVE 행이 있나?"**(`findNewestActive() != null`)만 봤다. 행은 있으니 "키 있네" 하고 새 키 생성을 스킵했는데, 그 행은 복호화 안 되는 죽은 키였다. **"키가 있다" ≠ "키를 쓸 수 있다"** 를 혼동한 것.

### 4.2 왜 화면엔 302(로그인 리다이렉트)로 보였나

키 0개 → 토큰 서명 시 500 이 나야 정상인데, 실제 응답은 302 → /login 이었다.

1. 토큰 발급 중 500 발생
2. 서블릿 컨테이너가 내부적으로 `/error` 로 ERROR 포워딩
3. auth-server 일반 시큐리티 체인(`@Order(2)`)이 `/error` 를 `authenticated()` 로 막고 있었음(permitAll 에 `/actuator/**` 만 있고 `/error` 누락)
4. 미인증 → 로그인 폼으로 302

**진짜 원인(500)이 인증 문제(302)로 둔갑**했고, 이 한 함정이 막판 디버깅을 시큐리티 토끼굴로 끌고 갔다. 결정적 단서는 보안 TRACE 로그에서 원 요청(`/oauth2/token`)이 아니라 **`Authorizing GET /error`** 가 찍힌 것 — 내부 ERROR 포워딩 신호.

### 4.3 영구 수정 2건

| 수정 | 내용 |
|---|---|
| ① `hasUsableActiveKey()` | `ensureBootstrapKey()` 가 "행 존재"가 아니라 **`RSAKey.parse(cipher.reveal(...))` 성공 여부**(=복호화·파싱까지 가능한가)로 판정. 불가하면 새 ACTIVE 부트스트랩. 복호화 불가 키는 파괴하지 않고 `loadFromDb` 가 격리(롤백/감사 안전) |
| ② `/error` permitAll | `defaultSecurityFilterChain` permitAll 에 `/error` 추가(`requestMatchers("/actuator/**", "/error")`). 내부 500 이 정직하게 5xx 로 드러나게 함 |

### 4.4 즉시 해소 / 진단 절차

```bash
# 진단: jwk_json head 가 enc: 면 암호문
kubectl exec statefulset/postgres -- \
  psql -U postgres -d authdb \
  -c "SELECT kid,status,left(jwk_json,16),length(jwk_json) FROM auth_signing_key;"

# 샌티/dev 즉시 해소: 죽은 키 삭제 후 재기동 → 부트스트랩이 현재 마스터키로 새 키 생성
kubectl exec statefulset/postgres -- psql -U postgres -d authdb -c "DELETE FROM auth_signing_key;"
kubectl rollout restart deploy/auth-server
```

운영에서는 함부로 DELETE 하지 말고 **시크릿 정합(옛 AES_SECRET 복원)** 또는 정식 키 재생성 절차를 따른다.

---

## 5. 기억할 원칙

- **DB 에 들어가는 건 RSA 개인키(암호문)이지 AES 가 아니다.** AES 마스터키는 env(Secret)에 있다.
- **"키가 있다" ≠ "키를 쓸 수 있다."** 마스터키 교체 가능성이 있는 한, 부트스트랩/헬스는 행 존재가 아니라 복호화 가능 여부까지 확인한다(`hasUsableActiveKey`).
- **표면 증상(302)이 진짜 원인(500)을 가린다.** 커스텀 시큐리티 체인은 `/error`(+`/actuator/**`)를 permitAll 해 에러를 마스킹하지 말 것.
- **서명은 최신 ACTIVE 1개, 검증은 ACTIVE+RETIRED 전부.** 회전 오버랩이 회전 직후 토큰을 살린다.
- **내부(HMAC)와 외부(AS RS256)는 다른 발급기.** RSA 서명키 이야기는 AS 경로에 한정된다.

---

## 6. 관련 소스 (master 기준)

| 영역 | 경로 |
|---|---|
| 발급자 종류 enum | `framework/framework-security/.../jwt/TokenIssuerKind.java` |
| 내부 HMAC 발급 | `framework/framework-security/.../jwt/JwtProvider.java` |
| 다운스트림 이중검증 | `framework/framework-security/.../jwt/{ResourceServerJwtVerifier,DownstreamTokenAuthenticator}.java` |
| AES 컬럼 암호화 | `services/auth-server/.../jose/{SigningKeyCipher,AesSigningKeyCipher}.java` |
| 서명키 엔티티/매퍼 | `services/auth-server/.../jose/{SigningKey,SigningKeyMapper}.java` |
| JWK 소스(부트스트랩/로드) | `services/auth-server/.../jose/JdbcRotatingJwkSource.java` |
| 회전 | `services/auth-server/.../jose/{SigningKeyRotationService,SigningKeyRotationScheduler}.java` |
| AS 시큐리티 체인 | `services/auth-server/.../config/AuthorizationServerConfig.java` |
| 함정 원본 | `docs/guide/PITFALLS.md` §5 |
