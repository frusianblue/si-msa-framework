# NEXT_SESSION_KICKOFF.md — 다음 세션 즉시 착수 시트 (관측/Observability)

> **이 파일의 용도**: 다음 세션을 열자마자 복사해 그대로 쓰는 "킥오프 + 인계 요약" 한 장.
> 직전 세션(2026-06-02, SI 공통 유틸)까지의 상태를 반영했고, **다음 작업=관측(observability)** 가정으로 미리 채워 둠.
> 다른 작업을 고르면 "이번 세션 목표" 절만 바꾸면 된다. 전체 맥락은 `HANDOFF_SUMMARY.md`/`HANDOFF.md`, 빈 양식은 `HANDOFF_SUMMARY_TEMPLATE.md`.

---

## 0. 세션 시작 시 첫 3가지 (복붙용)
1. repo 최신화: `git pull` 후 `./gradlew :framework:framework-core:test` 로 직전 유틸 빌드/테스트 통과 재확인(launcher 픽스 반영 확인).
2. 직전 상태 읽기: `HANDOFF_SUMMARY.md`(세션 한 장) → 막히면 `HANDOFF.md` 6절(함정)·`STACK.md` 5절(Boot4 주의).
3. 이번 작업 범위 확정 후 아래 "이번 세션 목표"의 〈…〉를 채우고 진행.

## 1. 지금까지 (Done — 2026-06-02 기준)
- **완료**: 코어/기본 + 토대4 + 보안완성(ISMS-P) + 데이터/연계(금융) + 업무생산성3 + 규제특화(mfa) + **SI 공통 유틸(core/util)**.
- **직전 세션**: `framework-core/util` 에 검증·마스킹·날짜/영업일·금액·한글·해시·JSON 유틸 추가(빈 없는 순수 정적, 외부 의존성 0, JSON 만 Jackson3). `CoreUtilsTest` 동봉.
- **빌드 인프라 픽스**: 루트 `subprojects` 에 `testRuntimeOnly junit-platform-launcher` 추가 → Gradle 9 에서 테스트 발견 단계 실패 해소(전 모듈 공통).
- ⚠️ 받는 쪽 미확인 시: `./gradlew :framework:framework-core:compileJava :framework:framework-core:test` + `./gradlew spotlessApply`.

## 2. 이번 세션 목표 (관측 가정 — 다른 작업이면 이 절만 교체)
**모듈**: `framework-observability` (신규, 선택형 `framework.observability.enabled=false`)
**책임(초안)**: 구조화(JSON) 로그 · Micrometer 메트릭(공통 태그/네이밍) · OTel 트레이스 익스포터 표준화.
**현 자산**: core 에 `io.micrometer:micrometer-tracing-bridge-otel` 이미 보유(api), `MdcTraceFilter`(traceId MDC), `logback-common.xml`(traceId 패턴·AUDIT 파일). → **분산추적 토대는 있음**, 이번엔 그 위에 메트릭/로그/익스포터 표준을 얹는 것.
**확정할 결정(세션 초반에)**:
- 〈익스포터 타깃: OTLP(Collector) vs Prometheus 스크레이프 vs 둘 다〉
- 〈로그 포맷: logback JSON encoder 도입 여부(BOM 밖 라이브러리면 카탈로그 핀 필요) vs 기존 패턴 유지+MDC 확장〉
- 〈공통 메트릭 태그: service/env/version 등 표준 키 — k8s 라벨과 정렬〉
- 〈k8s 연계: actuator `/actuator/prometheus` 노출 + ServiceMonitor 가정 여부〉

## 3. 착수 전 확인할 것 (관측 특화)
- Boot 4 actuator/micrometer autoconfigure FQCN 을 **공식 소스로 확정**(afterName 정확히). Boot 4 패키지 분리 주의.
- 새 라이브러리(예: `logstash-logback-encoder` 등)는 **BOM 밖이면** `libs.versions.toml`+루트 `ext` 핀, `implementation`.
- 메트릭/트레이스 익스포터는 런타임 비용 → 기본 off, 토글로만 활성.

## 4. 모듈 추가 레시피 (요약 — 상세는 템플릿 A절)
1. `framework/framework-observability/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN).
2. `build.gradle`: 능력전이=api · 내부구현=implementation · 호스트/선택=compileOnly(+test). BOM 밖 새 라이브러리만 카탈로그 핀.
3. `settings.gradle`(신규 모듈)·`imports`(새 autoconfig) 등록 — 누락 주의. **테스트 넣으면 launcher 는 루트에서 이미 적용됨(추가 불필요).**
4. 오토컨피그: `@AutoConfiguration(afterName=…)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`.
5. 검증: `compileJava`(+`test`)(+`spotlessApply`). 알고리즘성 로직 있으면 순수 JDK 실행검증.
6. 드롭인 zip(변경 파일 전부 + settings/imports/카탈로그·문서) → 루트 `unzip -o`.

## 5. 세션 종료 시 할 일 (인계)
- `HANDOFF_SUMMARY.md` 갱신구간을 **이번 세션 내용으로 교체**(양식은 `HANDOFF_SUMMARY_TEMPLATE.md` B절 복사).
- 구조/원칙/함정 변경 시 `HANDOFF.md`(1·6·7절) + 새 모듈이면 `docs/FRAMEWORK_MODULES.md`(0·2.7·4절) + `STACK.md`(새 라이브러리) 갱신.
- 다음 세션용으로 이 파일(`NEXT_SESSION_KICKOFF.md`)의 "이번 세션 목표"를 그다음 작업으로 갱신.

---
*직전 세션 산출물: si-utils-core-2026-06-02.zip(유틸 9 + 테스트 + 문서3) · si-utils-junit-fix-2026-06-02.zip(루트 build.gradle + HANDOFF). 둘 다 루트에서 unzip -o.*
