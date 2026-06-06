# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ §S3' standalone-kind AS client_credentials 토큰 = 완전 종료(2026-06-06). jwks=200·token=HTTP 200+access_token 실측.** 잔여였던 "토큰 1줄"이 사실은 **표면 302가 진짜 원인을 가린 버그**였다. 증상: `POST /oauth2/token`(정상 secret)·`GET /oauth2/jwks`=302→/login, discovery 만 200. **여러 턴 동안 시큐리티 체인(`exceptionHandling`/매처)을 오진**하다, 보안 WEB TRACE 의 `Authorizing GET /error`(원 요청 아님)에서 전환 → 진짜 인과 확정: `auth_signing_key` 의 ACTIVE 키(`enc:` AES 암호문)가 **현재 `AES_SECRET` 으로 복호화 불가**(시크릿 바뀐 채 PVC DB 잔존) → `ensureBootstrapKey()` 가 `findNewestActive()!=null`(행 존재)만 보고 새 키 생성 스킵 → `loadFromDb` 가 그 키 스킵 → JWKS 0개 → 500 → `/error` → `@Order(2)` 체인이 막아 302 둔갑. **즉시 해소** = `DELETE FROM auth_signing_key`+`rollout restart`(부트스트랩 새 키). **코드 영구화 2건 + 재배포 도구 1건** 반영. **모든 변경 uncommitted → 다음 세션 첫 행동 = commit/push.**

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: §S3' 토큰 마감 세션(서명키 복호화 트랩·`/error` 마스킹·redeploy 도구)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(auth-server 2파일 + devops 스크립트 + 문서).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| 302 근본원인 규명 | TRACE(`LOGGING_LEVEL_…SECURITY_WEB=TRACE`)에서 `Authorizing GET /error` 포착 → 로그 `JdbcRotatingJwkSource: 서명키 파싱/복호화 실패 — 건너뜀` + `IllegalStateException: 사용 가능한 서명키 없음` 확인. `auth_signing_key` head=`enc:`(AES 암호문), 현재 `AES_SECRET` 으로 복호화 불가. |
| 즉시 해소 | `psql -d authdb -c "DELETE FROM auth_signing_key;"` → `rollout restart deploy/auth-server` → 부트스트랩 새 ACTIVE 키 → **jwks=200·token=200+access_token 실측 ✅**. |
| 코드 영구화(2) | ① `JdbcRotatingJwkSource.ensureBootstrapKey()` → `hasUsableActiveKey()`(복호화/파싱 성공까지 확인, 불가하면 새 키 부트스트랩; 옛 키 비파괴 보존) ② `AuthorizationServerConfig.defaultSecurityFilterChain` permitAll 에 `/error` 추가. 부수: `exceptionHandling` 을 SS7 정본(`defaultAuthenticationEntryPointFor(LoginUrl, MediaTypeRequestMatcher(TEXT_HTML))`)으로 정렬. |
| 재배포 도구 | `deploy/k8s/standalone-kind/redeploy.sh` 신설 — 소스 다이제스트 태그(`src-<sha1>`)로 빌드→push→`set image`→rollout(+`--smoke` 토큰). 고정 태그 캐시 함정 영구 제거. |
| 함정/문서 | PITFALLS §5 신규 2건(서명키 복호화→부트스트랩 차단 / `/error` 미허용 마스킹) + curl `*/*` TEXT_HTML 메모 + 자가진단 2행. HANDOFF §6(상세)·§7(🟢+백로그⑤)·이 SUMMARY 갱신. |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드, dev overlay, 6파드 Running. `harbor-auth-reg`(harbor.local) 유지. auth-server 파드 imagePullPolicy=Always(세션 중 변경) — dev 루프엔 무방.
- **서명키**: `auth_signing_key` 에 현재 마스터키로 부트스트랩된 ACTIVE 키 1개(복호화 가능). 옛 `enc:` 죽은 키는 DELETE 됨.
- **그린 기준**: 6파드 ✅ · DB authdb/sidb/admindb ✅ · **AS jwks=200·token=200 ✅** = §S3' 전 구간 그린.
- **미커밋**: ⑤ auth-server 2파일 + redeploy.sh + PITFALLS/HANDOFF/SUMMARY (앞 세션 ④ standalone-kind 트랙도 여전히 미커밋).

## 바로 다음 할 일 (Next)
1. **commit/push 먼저(그린 박제)** — 누적 ④+⑤ 한 묶음:
   `git add -A && git commit -m "fix(auth-server): 서명키 복호화 불가 ACTIVE 키가 부트스트랩 차단 → JWKS 0개/토큰 500 수정(hasUsableActiveKey) + /error permitAll(에러 마스킹 제거) + redeploy.sh(다이제스트 태그); standalone-kind 트랙/overlay" && git push origin master`
2. **(받는 쪽 빌드 검증)** — 작성환경 Gradle 불가 → `./gradlew :services:auth-server:test spotlessApply`(특히 `JdbcRotatingJwkSource`/`AuthorizationServerConfig` 컴파일 + 기존 `TokenIssuanceRoundTripTest` 회귀). 이미 standalone-kind 에서 런타임 그린이므로 회귀 위험 낮음.
3. **S4 애드온** — metrics-server(HPA, kind 는 `--kubelet-insecure-tls`) → kube-prometheus-stack(설치 후 dev overlay ServiceMonitor `$patch:delete` 해제). 이후 S5 prod-rehearsal → S6 상위흐름(OIDC RP·이중발급기) → S7 Jenkins(sha 핀 자동 — redeploy.sh 의 다이제스트 태그 발상을 CI 로 승격).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **표면 증상(302)이 진짜 원인(500)을 가린다** — 커스텀 시큐리티 체인은 `/error`(+`/actuator/**`)를 permitAll 해야 내부 예외가 인증 리다이렉트로 둔갑하지 않는다. TRACE 에 원 요청 아닌 `Authorizing GET /error` 가 보이면 내부 ERROR 포워딩 신호.
- **"키가 있다" ≠ "키를 쓸 수 있다"** — 부트스트랩/헬스는 행 존재가 아니라 **복호화 가능 여부**까지 본다(AES_SECRET 교체 시 옛 키가 새 키 생성을 막던 트랩). `hasUsableActiveKey()`.
- **AES_SECRET 교체 + PVC DB 잔존 = 서명키 복호화 불가** — 샌티/dev 는 `DELETE FROM auth_signing_key`+재기동으로 재부트스트랩. 운영은 시크릿 정합 또는 키 재생성 절차 필요.
- **로컬 반복 배포는 고정 태그 말고 콘텐츠 다이제스트 태그** — `:local`/`:dev` + `IfNotPresent` 는 "반영됐나" 실랑이를 부른다. `redeploy.sh` 의 `src-<sha1>` 가 이를 제거(노드/호스트 옛 이미지 무관).
- **호스트 자바 불필요** — 이미지는 `Dockerfile.build`(컨테이너 안 Gradle)로 빌드. `bootBuildImage`/호스트 gradlew 아님.
- (유지) wrong-secret→401 은 client-auth 필터 자체 거부라 entry point/매처와 무관 — 302 진단에 끌려가지 말 것.

<!-- 갱신 끝 -->
