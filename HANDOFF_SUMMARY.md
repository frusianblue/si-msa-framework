# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**YAML(설정값) 패스워드 암호화** 완료 — 직전 세션 최우선(#0). `framework-core` 에 커스텀 Boot4 **`EncryptedPropertyEnvironmentPostProcessor`** + enumerable 지연 래퍼 **`DecryptingPropertySource`** 신설: `application*.yml`/env 의 `ENC(...)` 값을 기동 시 기존 **`AesCryptoService`(AES-GCM)** 로 복호화. 등록은 framework-core **신설 `META-INF/spring.factories`**(`org.springframework.boot.EnvironmentPostProcessor` 키). 마스터키=`framework.crypto.aes-secret`/`AES_SECRET`(평문 주입, ENC 불가=닭달걀). 토글 `framework.crypto.config-decryption.enabled`(**기본 on** — ENC 없으면 완전 무동작이라 안전). 토큰 생성 CLI **`CryptoCli`**, prod 마스터키 가드 **`AesMasterKeySafetyGuard`**(JWT 가드 패턴) 동봉. **Jasypt 미도입·신규 외부 의존성 0·Jackson 무관.** **다음 세션 최우선 = (devops) CI 게이트 + 멀티모듈 jacoco 집계.**

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: YAML 설정값 암호화 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### 설정값 암호화 (framework-core, 신규 외부 의존성 0)
1. **EPP 진입점** `EncryptedPropertyEnvironmentPostProcessor`(`org.springframework.boot.EnvironmentPostProcessor`, `getOrder=LOWEST_PRECEDENCE`): ① 토글 off면 무동작 ② 어느 소스에도 `ENC(...)` 없으면 무동작(마스터키 불요) ③ ENC 있으면 마스터키로 `AesCryptoService` **직접 생성**(EPP 는 빈 못 씀) ④ 각 `EnumerablePropertySource` 를 래퍼로 replace.
2. **지연 복호화 래퍼** `DecryptingPropertySource extends EnumerablePropertySource`: 값 읽는 시점에만 `ENC(...)` 복호화(평문은 위임), `getPropertyNames()` 위임(바인딩 보존). 프로파일별 yaml 늦게 들어와도 누락 없음.
3. **등록** framework-core 신설 `META-INF/spring.factories`(`.imports` 아님 — 컨텍스트 이전 동작). observability EPP 와 동일 패턴.
4. **토글 필드** `CryptoProperties.ConfigDecryption.enabled`(기본 true) 추가 — 바인딩/메타데이터 일관성(판정은 EPP 가 env 직접 읽음).
5. **CLI** `CryptoCli encrypt <평문>`(`AES_SECRET` env) → `ENC(...)` 출력(암호화 엔드포인트 노출 대신 CLI).
6. **prod 마스터키 가드** `AesMasterKeySafetyGuard`(`InitializingBean`, JWT 가드 패턴): prod 에서 비었거나·기본 placeholder(`change-me`/`change-this`)·16바이트 미만이면 부팅 실패, 비-prod 는 경고. `CryptoAutoConfiguration` 에 빈 등록.
7. **테스트** EPP 7(복호화/평문보존/getPropertyNames보존/ENC無 무동작/마스터키無 실패/마스터키ENC 실패/오키 읽기시 복호화실패/토글off 리터럴) + 가드 6(prod 기본·빈·짧음 실패 / prod 강한키 ok / 비-prod 경고 / 16B 경계).
8. **문서 동기화**: STACK.md(표 ✅)·README.md(ENC 사용법+토글)·docs/FRAMEWORK_MODULES.md(core 책임 확장·완료)·docs/BASELINE_FEATURES.md(✅)·HANDOFF.md(§6 함정·§7 완료·다음순위)·본 SUMMARY.

## 현재 상태 (적용/검증)
- ✅ **AES-GCM 바이트 스킴(SHA-256 키파생·IV(12B)선두·128bit태그·Base64) + ENC 토큰 substring 추출을 독립 재현 5/5 통과**(round-trip·랜덤IV·분류·오키 인증실패·유니코드).
- ⚠️ 작성 환경이 **JRE-only(javac 없음) + Maven Central 차단** → Spring 의존부(EPP/가드/테스트)의 **gradle 컴파일은 미실행**. 신규 클래스는 archtest 5규칙(순환·Jackson3·레이어·네이밍·필드주입) 정적 검토상 무충돌(전부 `core.crypto` 동일 모듈·Jackson 0·생성자주입·`*AutoConfiguration`/`*Properties` 비충돌).
- 신규 파일 6: `EncryptedPropertyEnvironmentPostProcessor`·`DecryptingPropertySource`·`CryptoCli`·`AesMasterKeySafetyGuard`(+test 2) · `META-INF/spring.factories`. 수정 2: `CryptoProperties`(토글 필드)·`CryptoAutoConfiguration`(가드 빈). 문서 6.

## 켜는 법
```bash
# 1) 토큰 생성(개발자 1회) — 마스터키는 env 로만
AES_SECRET='프로젝트마스터키' java -cp <classpath> com.company.framework.core.crypto.CryptoCli encrypt 'sipass'
#   → ENC(Qk9k...) 를 application*.yml 에 붙여넣기
# 2) yaml
#   spring.datasource.password: ENC(Qk9k...)
#   framework.crypto.aes-secret: ${AES_SECRET}   # 평문 주입(ENC 불가)
# 3) 기동 — AES_SECRET 주입하면 자동 복호화
AES_SECRET='프로젝트마스터키' ./gradlew :services:user-service:bootRun
```
- 토글 끄기: `framework.crypto.config-decryption.enabled=false`(끄면 ENC 리터럴 그대로 → 권장하지 않음).
- 검증(받는 쪽): `./gradlew :framework:framework-core:test :framework:framework-archtest:test spotlessApply` + 한 서비스에서 `ENC()` 실제 기동.

## 바로 다음 할 일 (Next)
1. **★ (devops) CI 게이트** — `:framework-archtest:test` + 전 모듈 `:test` 를 PR 차단 게이트로. + **멀티모듈 jacoco 집계 리포트**(루트 aggregate).
2. (devops) **그릇 정비** — 게이트웨이 런타임 점검(CORS preflight·rate-limit 429)·k8s 멀티서비스(redis/secret/configmap·observability ServiceMonitor 실배포)·CI-CD 멀티서비스화.
3. (선택) 카탈로그 §6 대기열: 아카이빙/압축·일괄 처리 / 서버측 S3 멀티파트 병렬 업로드(TransferManager).
4. (선택) **마스터 키 회전(rotation)** 절차/스크립트(구키 복호→신키 재암호) — 설계서 §6 백로그.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **설정값 암호화 토글은 기본 on**(일반 "3단 토글 기본 off" 규약의 의도적 예외): `ENC(...)` 가 없으면 EPP 가 완전 무동작이라 켜 둬도 무해하고, 오히려 off면 토글을 잊었을 때 `ENC(...)` 리터럴이 그대로 다운스트림(예: DB 패스워드)으로 흘러 **조용히 깨진다**. 그래서 안전 측면에서 on.
- **EPP 등록은 framework-core 신설 `META-INF/spring.factories`**(`org.springframework.boot.EnvironmentPostProcessor` 키, `.imports` 아님). framework-core 엔 spring.factories 가 없었음 → 신설. 키 한 글자만 틀려도 조용히 미등록=복호화 안 됨.
- **마스터키는 `ENC()` 불가**(닭-달걀) → `framework.crypto.aes-secret`/`AES_SECRET` 는 항상 평문(env/시크릿). 코드가 마스터키 값이 `ENC(`로 시작하면 기동 실패시킴.
- **enumerable 래퍼는 `getPropertyNames()` 도 위임 필수** — 누락하면 Binder 가 ENC 값을 못 봐 바인딩 실패. 지연 복호화라 사전 스캔 단계(`containsEncrypted`)는 복호화하지 않고 ENC 존재만 본다 → 오키/조작값은 **읽는 시점**에 GCM 인증 실패로 기동 중단(조용히 통과 안 함).
- **복호화 실패=기동 실패가 정답**. 예외 메시지/로그에 평문·키 노출 금지(래퍼는 복호화값 로깅 안 함).
- **운영 정책=권장 (a)**: prod 비밀은 env/시크릿 주입 유지, ENC 는 dev 편의 + 저장소에 평문 안 남기기 용도(prod yaml 의 `${DB_PASSWORD}` 와 ENC 를 같은 값에 이중 적용하지 말 것).
- **작성 환경 JRE-only**: 컨테이너에 javac 없음 → 순수 JDK 로직도 Python 재현으로 검증. Spring 부는 받는 쪽 gradle 컴파일 필수.
- (지난·유효) 토글3단 기본 off(이번 암호화 토글은 예외) / Jackson3(`tools.jackson.*`, annotation만 예외) / compileOnly 타입 test 재선언 / `.imports` 가드 / EPP 는 spring.factories(Boot4 패키지) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 감사로그 DB 적재 3요건 / JWT·DevAuth·Password·AES 마스터키 prod 가드 / 필터·EPP 는 GlobalExceptionHandler 밖 / Boot4 패키지 리네임 추측 금지.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증 가능하게.
2. 기존 인터페이스는 건드리지 말고 capability 인터페이스로 확장(ISP). 생성자 변경 시 기존 오버로드 유지.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`. 의존성 추가는 곧 1단(모듈) 토글.
4. 새 오토컨피그면 `.imports`+가드 테스트. **EPP 는 `spring.factories`**(키 정확히, Boot4 패키지) — `.imports` 아님.
5. 오토컨피그 토글 + `@ConditionalOnMissingBean`. 선택 백엔드는 설정 분기.
6. 테스트: 핵심 알고리즘 단위(JDK) + 오토컨피그 빈 선택 + 동작 게이트. EPP 는 `StandardEnvironment`+`MapPropertySource` 로 단위 검증.
7. 드롭인: 변경 파일 전부 한 zip, 루트 `unzip -o`. 문서 동기화. 사용자 환경 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증.

<!-- 갱신 끝 -->
