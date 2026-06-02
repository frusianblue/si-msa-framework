# NEXT_SESSION_KICKOFF.md — 다음 세션 즉시 착수 시트

> **이 파일의 용도**: 다음 세션을 열자마자 복사해 그대로 쓰는 "킥오프 + 인계 요약" 한 장.
> 직전 세션(2026-06-02, **관측/observability**)까지 반영. **다음 작업 후보=규제특화 잔여 또는 그릇 정비** 가정.
> 다른 작업을 고르면 "이번 세션 목표" 절만 바꾸면 된다. 전체 맥락은 `HANDOFF_SUMMARY.md`/`HANDOFF.md`.

---

## 0. 세션 시작 시 첫 3가지 (복붙용)
1. repo 최신화: `git pull` 후 `./gradlew :framework:framework-observability:compileJava :framework:framework-observability:test` 로 직전(관측) 모듈 빌드/테스트 통과 재확인 + `./gradlew spotlessApply`.
2. 직전 상태 읽기: `HANDOFF_SUMMARY.md`(세션 한 장) → 막히면 `HANDOFF.md` 6절(함정)·`STACK.md` 5절(Boot4 주의).
3. 이번 작업 범위 확정 후 아래 "이번 세션 목표"의 〈…〉를 채우고 진행.

## 1. 지금까지 (Done — 2026-06-02 기준)
- **완료**: 코어/기본 + 토대4 + 보안완성(ISMS-P) + 데이터/연계(금융) + 업무생산성3 + 규제특화(mfa) + SI 공통 유틸(core/util) + **관측(observability)**.
- **직전 세션**: 신규 `framework-observability` — 공통 메트릭 태그(`MeterRegistryCustomizer`)·Boot4 네이티브 구조화(JSON) 로그·메트릭/트레이스 OTLP 익스포터 표준(전부 토글·기본 off). 프로퍼티성 표준값은 `EnvironmentPostProcessor`(로깅 초기화 전) 주입. **새 외부 의존성 0**(레지스트리/익스포터는 호스트 runtimeOnly opt-in, Boot BOM 관리). k8s `deploy/k8s/observability.yaml`(ServiceMonitor+스크레이프+프로브). 공통태그 로직 순수 JDK 검증 5/5.
- ⚠️ 받는 쪽 미확인 시: `./gradlew :framework:framework-observability:compileJava :framework:framework-observability:test` + `spotlessApply`. 특히 EPP 등록(`spring.factories`)·Boot4 `MeterRegistryCustomizer` FQCN 컴파일 통과 확인.

## 2. 이번 세션 목표 (다음 작업 — 골라서 이 절만 교체)
**후보 A — 규제특화 잔여(해당 사업만)**: 〈pki / hsm / recon(대사) / egov(전자정부 표준연계) 중 택1〉. 보안성 심의/공공 요건 매핑 먼저.
**후보 B — 그릇 정비(운영 토대 마감)**: 〈게이트웨이(폴백·CORS·rate-limit) / k8s 멀티서비스(redis·secret·configmap, observability ServiceMonitor 실배포) / CI-CD(멀티모듈 파이프라인)〉 중 택1.
**후보 C — (선택) idempotency `JdbcIdempotencyStore`** 추가(현재 memory/redis만).
→ 택1 후 모듈/책임/확정할 결정을 여기에 적고 3절로.

## 3. 착수 전 확인할 것 (공통)
- **추측 금지**: Boot4/Spring7/Jackson3 + 외부 API 는 **공식 소스(GitHub raw·공식 API 문서)로 확정**. 특히 Boot4 패키지 이동(관측에서 `MeterRegistryCustomizer` 이동 사례)·이중 프로퍼티 키.
- 새 라이브러리는 **BOM 밖이면** `libs.versions.toml`+루트 `ext` 핀, `implementation`. BOM 안이면 버전 미명시.
- 런타임 비용 큰 기능(익스포터·트레이싱)은 기본 off, 토글로만.

## 4. 모듈 추가 레시피 (요약)
1. `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). 컨텍스트 이전 동작 필요하면 **EPP + `spring.factories`**(관측 사례).
2. `build.gradle`: 능력전이=api · 내부구현=implementation · 호스트/선택=compileOnly(+test). "클래스 직접 참조 없이 런타임 classpath 로만" 동작하면 **호스트가 runtimeOnly opt-in**(모듈은 안 받음). BOM 밖만 카탈로그 핀.
3. `settings.gradle`(신규 모듈)·`imports`(새 autoconfig) 등록 — 누락 주의. **테스트를 넣으면 모듈 `build.gradle` 에 `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 도 같이**(JUnit5+AssertJ API). 루트 `subprojects` 가 깔아주는 `junit-platform-launcher` 는 *실행 런처*일 뿐 테스트 API 가 아님 — 빠지면 `package org.junit.jupiter.api does not exist` 로 테스트 컴파일 실패.
4. 오토컨피그: `@AutoConfiguration(afterName=…)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. (커스터마이저류는 Boot 가 모아 적용 → afterName 불요)
5. 검증: `compileJava`(+`test`)(+`spotlessApply`). 알고리즘성 로직은 순수 JDK 실행검증.
6. 드롭인 zip(변경 파일 전부 + settings/imports/문서) → 루트 `unzip -o`.

## 5. 세션 종료 시 할 일 (인계)
- `HANDOFF_SUMMARY.md` 갱신구간을 이번 세션 내용으로 교체(양식 `HANDOFF_SUMMARY_TEMPLATE.md` B절).
- 구조/원칙/함정 변경 시 `HANDOFF.md`(1·6·7절) + 새 모듈이면 `docs/FRAMEWORK_MODULES.md`(0·2.7·4절) + `STACK.md`(새 라이브러리/주의) 갱신.
- 다음 세션용으로 이 파일 "이번 세션 목표"를 그다음 작업으로 갱신.

---
*직전 세션 산출물: si-observability-2026-06-02.zip(framework-observability 모듈 + settings.gradle + deploy/k8s/observability.yaml + 문서6). 루트에서 `unzip -o`.*
