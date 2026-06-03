# NEXT_SESSION_KICKOFF.md — 다음 세션 즉시 착수 시트

> **이 파일의 용도**: 다음 세션을 열자마자 복사해 그대로 쓰는 "킥오프 + 인계 요약" 한 장.
> 직전 세션(2026-06-03, **파일 하드닝 묶음 #8+#9+#10 / framework-file* 확장**)까지 반영. **다음 작업 후보=(devops) CI 게이트 또는 카탈로그 §6 대기열(아카이빙·일괄처리) 또는 그릇 정비(k8s/CI-CD)** 가정.
> 다른 작업을 고르면 "이번 세션 목표" 절만 바꾸면 된다. 전체 맥락은 `HANDOFF_SUMMARY.md`/`HANDOFF.md`.

---

## 0. 세션 시작 시 첫 3가지 (복붙용)
1. repo 최신화: `git pull` 후 `./gradlew :framework:framework-file:test :framework:framework-file-s3:test :framework:framework-archtest:test spotlessApply` 로 직전(파일 하드닝) 통과 재확인.
2. 직전 상태 읽기: `HANDOFF_SUMMARY.md`(세션 한 장) → 막히면 `HANDOFF.md` 6절(함정)·`STACK.md` 5절(Boot4 주의).
3. 이번 작업 범위 확정 후 아래 "이번 세션 목표"의 〈…〉를 채우고 진행.

## 1. 지금까지 (Done — 2026-06-03 기준)
- **완료**: 코어/기본 + 토대4 + 보안완성(ISMS-P) + 데이터/연계(금융: datasource·messaging·**saga**) + 업무생산성3 + 규제특화(mfa) + SI 공통 유틸(core/util) + 관측(observability) + 독립 다중 DB + 요청컨텍스트(#5) + 이미지 처리(#7) + **파일 하드닝 묶음(#8+#9+#10)**.
- **직전 세션**: `framework-file`/`-s3` 에 **파일 하드닝 묶음(#8+#9+#10)** 확장(신규 모듈 아님). 기존 `FileStorage` 불변 + **capability 인터페이스(ISP)**: **#8** HTTP Range 206 다운로드(`ByteRange` RFC7233·`RangeReadableStorage`·FileSystem 부분읽기·206/416) + S3 presigned PUT/GET(`PresignedUrlStorage`·`S3Presigner` 빈·`/presigned`·`/presigned-upload`·`/presigned-complete`, 대용량 클라 직행). **#9** 확장자↔MIME 계열 정합(`ExtensionContentTypePolicy`+Tika 확장, `enforce-extension-match` 옵트인). **#10** ClamAV INSTREAM AV 게이트(`FileScanner` SPI+`NoOp`+`ClamavFileScanner` **순수 JDK 소켓**, `FileService` 저장 전 스캔, fail-closed 기본). **새 외부 의존성 0**(Range=JDK NIO, ClamAV=소켓, S3Presigner=awssdk:s3 포함). 순수 JDK 코어 javac+하니스 **35/35** + JUnit 6종. image deprecation(`PAYLOAD_TOO_LARGE`→`CONTENT_TOO_LARGE`) 동봉.
- ✅ **사용자 환경 빌드/테스트 통과 확인** — `:framework:framework-file:test :framework:framework-file-s3:test :framework:framework-archtest:test spotlessApply` 그린. ⚙️ S3 오토컨피그 테스트는 신규 `S3Presigner` 빈이 deep-stub mock `getRegion()`=null 로 터지던 것 → `S3Presigner` mock 빈 추가로 수정(운영은 region 기본값 정상).

## 2. 이번 세션 목표 (다음 작업 — 골라서 이 절만 교체)
**후보 A — (devops) CI 게이트 + 커버리지 집계**: `:framework-archtest:test` + 전 모듈 `:test` 를 PR 차단 게이트로 + 멀티모듈 **jacoco 집계 리포트**(루트 aggregate). GitHub Actions/Jenkins 중 대상 확정 먼저.
**후보 B — 카탈로그 §6 대기열 승격**: 〈**아카이빙/압축**(여러 파일 zip/tar 묶음·해제, framework-file 확장 vs 신규) / **일괄 처리(batch processing)**(여러 파일 동일 작업 일괄 — framework-batch 와 경계 정리 필수)〉 중 택1. `docs/BASELINE_FEATURES.md` §6.
**후보 C — 그릇 정비(운영 토대, k8s/CI-CD)**: 게이트웨이 런타임 점검(CORS preflight·rate-limit 429) / k8s 멀티서비스(redis·secret·configmap·ServiceMonitor 실배포) / CI-CD 멀티서비스화.
**후보 D — 파일 하드닝 후속(선택)**: 서버측 S3 멀티파트 병렬 업로드(TransferManager) · presigned 업로드 비동기 후처리 스캔(신뢰경계 밖 본문 검증) · 암호화 파일 키 회전.
**후보 E — 규제특화 잔여(해당 사업만)**: pki / hsm / recon(대사) / egov 중 택1.
→ 택1 후 모듈/책임/확정할 결정을 여기에 적고 3절로.

## 3. 착수 전 확인할 것 (공통)
- **추측 금지**: Boot4/Spring7/Jackson3 + 외부 API 는 **공식 소스(GitHub raw·공식 API 문서)로 확정**. 특히 Boot4 패키지 이동(관측 `MeterRegistryCustomizer`·jdbc `DataSourceAutoConfiguration` 이동 사례)·이중 프로퍼티 키.
- 동적/개수 가변 빈은 **`ImportBeanDefinitionRegistrar`**(BDRPP 아님 — `@AutoConfiguration` before-순서 보존, Boot 백오프). 정적 빈은 `@AutoConfiguration` + `@ConditionalOnMissingBean`.
- 새 라이브러리는 **BOM 밖이면** `libs.versions.toml`+루트 `ext` 핀, `implementation`. BOM 안이면 버전 미명시.
- 런타임 비용 큰 기능(익스포터·트레이싱)은 기본 off, 토글로만.

## 4. 모듈 추가/확장 레시피 (요약)
1. 신규 `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). 컨텍스트 이전 동작 필요하면 **EPP + `spring.factories`**(관측 사례). **개수 가변 빈은 `ImportBeanDefinitionRegistrar`**(multi-DB 사례). 확장이면 기존 모듈에 패키지 추가 + imports 에 새 autoconfig 줄.
2. `build.gradle`: 능력전이=api · 내부구현=implementation · 호스트/선택=compileOnly(+test 재선언). "클래스 직접 참조 없이 런타임 classpath 로만" 동작하면 **호스트가 runtimeOnly opt-in**. BOM 밖만 카탈로그 핀.
3. `settings.gradle`(신규 모듈)·`imports`(새 autoconfig) 등록 — 누락 주의. **테스트를 넣으면 모듈 `build.gradle` 에 `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 도 같이**(JUnit5+AssertJ API). 루트 `subprojects` 가 깔아주는 `junit-platform-launcher` 는 *실행 런처*일 뿐 — 빠지면 `package org.junit.jupiter.api does not exist` 로 테스트 컴파일 실패. compileOnly(jdbc/web/mybatis/redis) 클래스를 테스트가 쓰면 **test 소스셋에 재선언**.
4. 오토컨피그: `@AutoConfiguration`(필요 시 `before`/`beforeName`/`afterName` 으로 Boot 백오프·순서) + `@ConditionalOnClass/Property` 3단 + 빈 `@ConditionalOnMissingBean`.
5. 검증: `compileJava`(+`test`)(+`spotlessApply`). 조용히 틀리는 결정/알고리즘 로직은 **순수 코어로 분리해 JDK 단독 실행검증**(multi-DB `MultiDataSourcePlan` 13/13 사례). `-Xlint`/`this-escape` 경고도 확인.
6. 드롭인 zip(변경 파일 전부 + settings/imports/문서) → 루트 `unzip -o`.

## 5. 세션 종료 시 할 일 (인계)
- `HANDOFF_SUMMARY.md` 갱신구간을 이번 세션 내용으로 교체(양식 `HANDOFF_SUMMARY_TEMPLATE.md` B절). 구조 바뀐 세션이면 템플릿 A절(베이스라인)도 갱신.
- 구조/원칙/함정 변경 시 `HANDOFF.md`(1·6·7절) + 새 모듈/확장이면 `docs/FRAMEWORK_MODULES.md`(0·2.7·4절) + `STACK.md`(새 라이브러리/주의) 갱신. 사용법/데모 바뀌면 모듈 `README.md`.
- 다음 세션용으로 **이 파일** "이번 세션 목표"를 그다음 작업으로 갱신.

---
*직전 세션 산출물: file-hardening-8-9-10.zip(framework-file scan/validator/storage 신규+수정·framework-file-s3 Range/presigned·테스트 7·image deprecation 1줄·문서 7). 루트에서 `unzip -o`. 사용자 환경 빌드/테스트 통과 확인됨.*
