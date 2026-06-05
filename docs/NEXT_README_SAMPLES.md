# 다음 섹션 — 전 모듈 README 에 "실전 사용 예(코드)" 채우기

> **목표**: 모든 `framework/framework-*/README.md` 에 개발자가 실제 코드에서 어떻게 쓰는지 보여주는 **샘플 코드**를 넣는다. 설정(yml)만이 아니라 **Java(또는 curl) 사용 예**가 핵심.
>
> **철칙(저장소 그라운딩)**: 샘플은 반드시 **해당 모듈의 실제 API(클래스·SPI·애너테이션)를 읽고** 작성한다. 메모리/추측으로 찍어내면 틀린 예제가 양산되므로 금지. 각 모듈 작업 시 `framework/<module>/src/main/java` 의 공개 타입을 먼저 확인.

---

## README 샘플 코드 표준 (모든 모듈 공통)

기존 섹션 구조(`켜는 법` → `쓰는 법` → `끄는 법` → `덮어쓰기` → `버전 관리`)는 유지하고, 그 사이에 **`## 실전 사용 예 (코드)`** 섹션을 넣는다.

규칙:
1. **최소 1개의 Java 또는 curl/bash 샘플**. 직접 호출 API 가 없는 "투명" 모듈(redis/session/secure-web 류)은 — (a) 다른 모듈에서 어떻게 동작하는지, (b) 앱이 직접 쓸 수 있는 전이 빈(예: `StringRedisTemplate`), (c) 검증 방법(curl/redis-cli), (d) 덮어쓰기 빈 예시 중 해당하는 것으로 채운다.
2. **실제 타입명**을 쓴다(가짜 클래스명 금지). 프로젝트가 구현할 SPI 가 있으면 그 구현 예를 1순위로.
3. **복붙 가능**하게 — import 가 자명하지 않으면 패키지를 주석으로. 컴파일까지는 아니어도 시그니처는 실제와 일치해야 한다.
4. 토글/엔드포인트가 있으면 yml + curl 을 같이.
5. 길이는 핵심만 — 한 모듈당 1~5개 스니펫.

완료 기준(모듈 1개): README 에 `## 실전 사용 예 (코드)` 존재 + 코드펜스 짝수(짝 안 맞으면 렌더 깨짐) + 실제 API 대조 확인.

---

## 진행 현황 (2026-06-05 기준)

### ✅ 완료 (이번 섹션)
- **framework-security** — Authenticator 구현·JWT 로그인 curl·@AuthenticationPrincipal·@PreAuthorize·세션 로그인 curl
- **framework-redis** — framework-session 과의 관계(커넥션 공유/네임스페이스 분리)·StringRedisTemplate 직접 사용·커스텀 TokenStore 빈
- **framework-session** — 세션 로그인/로그아웃+CSRF curl·클러스터 검증(redis-cli)·JSON 직렬화 빈 교체

### 🔴 우선순위 1 — Java 샘플 0개 + 개발자가 코드를 직접 작성하는 모듈
| 모듈 | 넣을 샘플(예상) | 먼저 읽을 실제 타입 |
|---|---|---|
| framework-mybatis | 감사필드 자동주입 매퍼·암호화 타입핸들러 사용·CurrentUser | `support/*`, 타입핸들러, `@Mapper` 규약 |
| framework-oauth-client | 소셜 로그인 콜백 핸들러/성공 후 자체 JWT 수령 흐름 | `OAuthLoginService`, 핸들러, 프로퍼티 |
| framework-audit | 커스텀 감사 이벤트 발행·조회 API 호출 | 감사 이벤트/엔트리 타입, store SPI |
| framework-observability | 커스텀 메트릭(Micrometer)·MDC 태그 추가 | 공통태그 커스터마이저, 로그 구조 |
| framework-secure-web | 스크리닝 켜고 차단 동작 확인(curl)·예외 처리 | 필터/프로퍼티 |

### 🟠 우선순위 2 — Java 샘플 0개, 대부분 설정이지만 호출 예가 도움됨
| 모듈 | 넣을 샘플(예상) |
|---|---|
| framework-file-s3 | 업로드/다운로드/Range·presigned URL 호출 |
| framework-file-sftp | SFTP 저장/부분 다운로드 호출 |
| framework-saml-sp | SP 메타데이터·로그인 진입 흐름(curl/리다이렉트) |
| framework-openapi | 컨트롤러 springdoc 애너테이션 예 |
| framework-archtest | 새 아키텍처 규칙 1개 추가하는 법(테스트 작성 예) |

### 🟡 우선순위 3 — Java 샘플 1개(최소) → 풍부화 검토
archive, batch, cache-redis, client, commoncode, context, core, datasource, excel, file, file-batch, i18n, idempotency, idgen, image, lock, log-masking, messaging, mfa, notification, pdf, qr, saga
→ 이미 1개씩 있음. 시간 여유 시 "실제 개발 시나리오" 스니펫 1~2개 보강(없으면 그대로 둬도 표준 충족).

---

## 작업 절차 (모듈 1개당)
1. `framework/<module>/src/main/java` 의 공개 타입(인터페이스/애너테이션/오토컨피그 빈) 확인 — **읽고 나서** 작성.
2. README 의 `쓰는 법` 다음에 `## 실전 사용 예 (코드)` 삽입(또는 기존 빈약한 예를 표준 형태로 확장).
3. 코드펜스 짝수 확인: `grep -c '```' README.md` → 짝수.
4. 모듈 README 변경이므로 **문서 동반 규칙**상 추가 카탈로그 변경은 없음(내용 보강만). 단 새 토글/엔드포인트를 발견·문서화하면 `FRAMEWORK_MODULES.md`/`MODULE_COMPOSITION.md` 도 동기화.
5. 델리버리: 변경 README 들을 zip 으로(드롭인). 이번 표준대로 `PITFALLS.md` 에 모듈별로 새로 알게 된 함정 있으면 누적.

## 검증(작성 환경 제약)
Maven Central 차단 → 컴파일 불가. 샘플은 **실제 시그니처 대조**(소스 grep)로 검증하고, 컴파일 보장이 필요하면 받는 쪽 로컬에서 확인. 문서 변경은 테스트 영향 없음.
