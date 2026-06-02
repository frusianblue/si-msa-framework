# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**`framework-observability` 신규(운영/관측 모듈)** — core 의 분산추적 토대(`micrometer-tracing-bridge-otel`·`MdcTraceFilter`) 위에 **① 공통 메트릭 태그(`MeterRegistryCustomizer`: service/env/version+extra, 전 레지스트리) ② Boot4 네이티브 구조화(JSON) 로그(`logging.structured.format` ecs/logstash/gelf, 인코더 라이브러리 불필요) ③ 메트릭/트레이스 OTLP 익스포터 표준**을 토글로 얹음. 전부 기본 off, **새 외부 의존성 0**(레지스트리/익스포터는 호스트 `runtimeOnly` opt-in, 전부 Boot BOM 관리). k8s 샘플(ServiceMonitor+스크레이프+프로브) 동봉. 핵심 분기(트레이스 OTLP 키 이중화·`MeterRegistryCustomizer` 패키지 이동)는 공식 API 로 확정 후 작성.

## 최종 갱신
- 일자: 2026-06-02 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 모듈 `framework-observability`**(`com.company.framework.observability`):
  - `config/ObservabilityProperties`(`framework.observability.*` 토글: enabled·service·env·version·metrics{common-tags-enabled,extra-tags,otlp}·logging.structured{enabled,format,target}·tracing.otlp·endpoints{expose,probes-enabled}).
  - `config/ObservabilityAutoConfiguration`(`@ConditionalOnProperty enabled=true`) → 빈 **`frameworkObservabilityCommonTags`**(`MeterRegistryCustomizer<MeterRegistry>`, 공통태그). Boot 가 레지스트리 생성 시 커스터마이저를 모아 적용 → afterName 불요.
  - `config/ObservabilityEnvironmentPostProcessor`(EPP, `spring.factories` 등록) → 구조화 로그·액추에이터 노출·k8s 프로브·OTLP 익스포터 표준값을 **로깅 초기화 전**에 `addLast`(최저 우선순위, 앱 값 우선)로 주입.
  - `metrics/ObservabilityTags`(순수 JDK 태그 해석) + `ObservabilityTagsTest`(JUnit5+AssertJ, 5케이스).
  - resources: autoconfig `.imports`(1줄) + `META-INF/spring.factories`(EPP).
  - `framework/framework-observability/build.gradle` = `api project(core)` + configuration-processor(끝).
- **등록/배포**: `settings.gradle` 에 `include 'framework:framework-observability'`. k8s `deploy/k8s/observability.yaml`(ServiceMonitor·annotation 스크레이프·actuator ConfigMap·health 프로브).
- **문서**: 모듈 `README.md` 신규 · 루트 `README.md`(운영/관측 절) · `HANDOFF.md`(1·6·7절) · `docs/FRAMEWORK_MODULES.md`(0·2.7·4절) · `STACK.md`(5절, 새 라이브러리 0/FQCN 이동/이중 OTLP 키). **카탈로그/libs.versions.toml 무변경**.

## 현재 상태 (적용/검증)
- 정적 점검 통과: 패키지=디렉터리, 괄호 균형, **`com.fasterxml` 0건**, Boot4 FQCN(`org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer`).
- **공통태그 해석 로직 순수 JDK 실행검증 완료(5/5)**: 표준3태그·공백→unknown·trim·extra append/순서·extra override·불량 항목 무시.
- ⚠️ **gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽: `./gradlew :framework:framework-observability:compileJava :framework:framework-observability:test` + `./gradlew spotlessApply`. (launcher 픽스 이미 루트 적용됨 → 새 모듈 테스트 정상 실행 예상)
- 공식 소스로 확정한 사실: Boot4 구조화 로그 네이티브(`logging.structured.format`), `MeterRegistryCustomizer` 패키지 이동, 메트릭 OTLP(`management.otlp.metrics.export.{enabled,url}`), 트레이스 OTLP 브리지 키(`management.otlp.tracing.endpoint`) vs 신규 스타터 키.

## 켜는 법
- `implementation project(':framework:framework-observability')` + `framework.observability.enabled=true`.
- Prometheus: `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` → `/actuator/prometheus`(EPP 가 exposure 에 포함). OTLP: 메트릭 `micrometer-registry-otlp`·트레이스 `opentelemetry-exporter-otlp` 추가 + `…otlp.enabled=true`.
- 구조화 로그: `framework.observability.logging.structured.enabled=true`(format ecs 기본). k8s: `deploy/k8s/observability.yaml` 참고.

## 바로 다음 할 일 (Next)
1. 받는 쪽 `:framework:framework-observability:compileJava (+:test) +spotlessApply` 확인. 특히 EPP 등록(`spring.factories`)·Boot4 `MeterRegistryCustomizer` FQCN 컴파일 통과 확인.
2. (선택) user-service 에 observability 실적용 + ServiceMonitor 실배포로 `/actuator/prometheus` 스크레이프 e2e 확인(devops). Grafana 대시보드/알림 룰은 후속.
3. **다음 모듈** = 규제특화 잔여(pki/hsm/recon/egov, 해당 사업만) 또는 **그릇 정비**(게이트웨이 폴백·CORS·rate-limit / k8s 멀티서비스 / CI-CD). `NEXT_SESSION_KICKOFF.md` 의 "이번 세션 목표" 갱신해 둠.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **Boot4 패키지 이동**: `MeterRegistryCustomizer` = `org.springframework.boot.micrometer.metrics.autoconfigure`(3.x `actuate.autoconfigure.metrics` 아님). actuator 계열 FQCN 은 공식 API 로 확인. `MeterRegistry/Tag` 는 `io.micrometer.core.instrument.*` 유지.
- **구조화 로그는 빈 아님 → EPP**: `logging.structured.*` 는 로깅 시스템이 컨텍스트보다 먼저 읽음 → `EnvironmentPostProcessor`(ConfigData 이후·로깅 초기화 전, order=LOWEST)가 `addLast` 로 표준값 주입(앱 값 우선). EPP 등록은 `META-INF/spring.factories`(오토컨피그 `.imports` 아님).
- **OTLP 트레이스 키 이중 컨벤션**: 브리지 방식(core)=`management.otlp.tracing.endpoint`(+`opentelemetry-exporter-otlp`), 신규 스타터=`management.opentelemetry.tracing.export.otlp.endpoint`. 메트릭 OTLP=`management.otlp.metrics.export.{enabled,url}`. 메트릭 OTLP 레지스트리+OTel 메트릭 브리지 동시 사용 시 중복 — 한쪽만.
- **관측도 새 의존성 0**: build.gradle 은 `api project(core)`+configuration-processor 만(레지스트리/익스포터 클래스 직접 참조 0). 호스트가 runtimeOnly opt-in. 카탈로그/STACK 무변경.
- (기존) util vs support 구분 · 외국인번호 형식만 · 음력/대체공휴일 주입식 · JsonUtils=Jackson3 · JUnit launcher 루트 적용 · 범용 유틸 재발명 금지 · MFA SPI(9-arg)/`ApiResponse<Object>` · Jackson3 전역 · 신규 모듈 settings/imports 등록 · BOM 밖만 카탈로그 핀.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규: `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). 빈 없는 순수 유틸은 `core/util` 에 클래스만. **컨텍스트 이전 동작이 필요하면 EPP + `spring.factories`**(관측 사례).
2. `build.gradle`: 능력전이=`api`, 내부구현=`implementation`, 호스트/선택=`compileOnly`(+test). **레지스트리/익스포터처럼 "클래스 직접 참조 없이 런타임 classpath 로만" 동작하면 모듈은 받지 않고 호스트가 `runtimeOnly` opt-in.** BOM 밖 새 라이브러리만 카탈로그+ext 핀.
3. `settings.gradle`(신규 모듈) / `imports`(새 autoconfig) 등록 잊지 말 것.
4. 코드 전 **Boot4/Spring7/Jackson3 + 외부 API 를 공식 소스(GitHub raw/공식 API 문서)로 확정**(특히 Boot4 패키지 이동·이중 프로퍼티 키). 틀리면 조용히 잘못되는 알고리즘은 순수 JDK 로 실제 실행 검증.
5. 오토컨피그: `@AutoConfiguration(afterName=…)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. (커스터마이저류는 Boot 가 모아 적용 → afterName 불요)
6. 검증: `./gradlew :...:compileJava (+:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
