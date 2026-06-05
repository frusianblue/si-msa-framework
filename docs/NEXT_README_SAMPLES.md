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

## 진행 현황 (2026-06-05 — 전 모듈 완료)

### ✅ 완료 — framework-* 36개 전부 `## 실전 사용 예 (코드)` 보유 (헤더 1개·코드펜스 짝수 검증)
이번 일괄 작업으로 **남은 33개 모듈 + redis(표준 헤더 신설)** 을 모두 채웠다. 모든 샘플은 각 모듈 `src/main/java` 의 실제 공개 타입/메서드 시그니처를 읽고 작성(추측 금지 철칙 준수).

- **우선순위 1 (개발자 직접 작성)**: mybatis(BaseEntity·EncryptedStringTypeHandler·CurrentUserProvider)·oauth-client(OAuthUserResolver·authorize/callback)·audit(@AuditLog·AuditEventSink·AuditQueryService)·observability(MeterRegistry·MeterRegistryCustomizer)·secure-web(차단 curl 3종) — 완료
- **우선순위 2**: file-s3(presigned)·file-sftp(SftpCredentialProvider)·saml-sp(SamlUserResolver·메타데이터)·openapi(@Tag/@Operation)·archtest(@ArchTest 규칙 추가법) — 완료
- **우선순위 3 (1개 이상 보강)**: archive·batch·cache-redis·client·commoncode·context·core·datasource·excel·file·file-batch·i18n·idempotency·idgen·image·lock·log-masking·messaging·mfa·notification·pdf·qr·redis·saga — 완료
- **기존 완료(유지)**: security·session

검증: `for d in framework/framework-*; do grep -c '^## 실전 사용 예' "$d/README.md"; done` → 전부 1, `grep -c '```'` 전부 짝수.

### 참고 — 발견된 함정(누적됨)
- "문서엔 redis 완료" 였으나 라이브 레포엔 표준 헤더 부재 → `_internal/HANDOFF §6` / `guide/PITFALLS.md §8` 에 "완료 기록 ≠ 커밋됨" 항목 추가.

## 작업 절차 (모듈 1개당)
1. `framework/<module>/src/main/java` 의 공개 타입(인터페이스/애너테이션/오토컨피그 빈) 확인 — **읽고 나서** 작성.
2. README 의 `쓰는 법` 다음에 `## 실전 사용 예 (코드)` 삽입(또는 기존 빈약한 예를 표준 형태로 확장).
3. 코드펜스 짝수 확인: `grep -c '```' README.md` → 짝수.
4. 모듈 README 변경이므로 **문서 동반 규칙**상 추가 카탈로그 변경은 없음(내용 보강만). 단 새 토글/엔드포인트를 발견·문서화하면 `FRAMEWORK_MODULES.md`/`MODULE_COMPOSITION.md` 도 동기화.
5. 델리버리: 변경 README 들을 zip 으로(드롭인). 이번 표준대로 `PITFALLS.md` 에 모듈별로 새로 알게 된 함정 있으면 누적.

## 검증(작성 환경 제약)
Maven Central 차단 → 컴파일 불가. 샘플은 **실제 시그니처 대조**(소스 grep)로 검증하고, 컴파일 보장이 필요하면 받는 쪽 로컬에서 확인. 문서 변경은 테스트 영향 없음.
