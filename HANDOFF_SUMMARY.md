# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**(1) YAML 설정값 암호화 + (2) 아카이빙/압축** 두 건 완료. (1) `framework-core` 에 커스텀 Boot4 `EncryptedPropertyEnvironmentPostProcessor`+`DecryptingPropertySource` 로 `ENC(...)` 를 기존 `AesCryptoService`(AES-GCM) 지연 복호화(`spring.factories` 등록, 마스터키 `AES_SECRET` 평문 주입, 토글 기본 on, `CryptoCli`/`AesMasterKeySafetyGuard` 동봉). (2) **신규 모듈 `framework-archive`** — `Archiver` SPI + `ZipArchiver`(순수 JDK `java.util.zip`): ZIP 다중엔트리 + GZIP 단일스트림, **스트리밍(transferTo)**·**zip-slip 차단**·**압축폭탄 가드**(엔트리수/엔트리크기/총바이트). 둘 다 **신규 외부 의존성 0**. **tar/tar.gz 는 JDK 미지원 → commons-compress 옵트인 후속**(코어는 의존성 0 유지). 기본기능 카탈로그 11/11(#4 리포트만 🟡 보류). 추가로 **공통 유틸 6종**(Io/Csv/FixedWidth/Charset/Text/Collection) 도 `framework-core/util` 에 신설(RetryUtils 제외). **다음 세션 최우선 = 파일 일괄처리(`framework-file-batch`, 설계서 `docs/NEXT_FILE_BATCH_PROCESSING.md`).**

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: YAML 암호화 + 아카이빙 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### A. 설정값(YAML) 암호화 (framework-core, 의존성 0)
- `EncryptedPropertyEnvironmentPostProcessor`(LOWEST_PRECEDENCE) + `DecryptingPropertySource`(enumerable 지연 래퍼, `getPropertyNames` 위임). 등록=framework-core 신설 `META-INF/spring.factories`. 마스터키=`framework.crypto.aes-secret`/`AES_SECRET`(평문, ENC 불가). 토글 `framework.crypto.config-decryption.enabled` 기본 on. `CryptoCli`(토큰 생성)·`AesMasterKeySafetyGuard`(prod 가드). 테스트 EPP 7 + 가드 6.

### B. 아카이빙/압축 (framework-archive 신설, 의존성 0)
- `Archiver` SPI(`zip`/`unzip`/`unzipToDirectory`/`gzip`/`gunzip`) + `ZipArchiver`(JDK `java.util.zip`). `ArchiveEntry`(record, `ContentSupplier` 지연 스트림)·`ArchiveSafety`(zip-slip 정규화/거부)·`ArchiveErrorCode`(`ARC****`)·config(`ArchiveProperties`/`ArchiveAutoConfiguration`+`.imports`).
- **스트리밍**: zip 생성·해제 모두 `transferTo`, unzip 은 엔트리 단위 콜백(`EntryConsumer`) — 전체 버퍼링 없음. 호출자 입/출력 스트림 비폐쇄(NonClosing 래퍼).
- **보안**: zip-slip(`../`/절대/드라이브 거부, `resolveSafely` baseDir 밖 거부) + 압축폭탄 가드(`BombGuardInputStream`: 엔트리크기·총바이트, `maxEntries`).
- settings include + archtest testImplementation + `.imports` + 등록가드 테스트. 테스트 3(Safety/ZipArchiver/AutoConfig).

### C. 문서 동기화
- STACK·README·FRAMEWORK_MODULES·BASELINE_FEATURES·HANDOFF·SUMMARY. BASELINE §6 대기열의 "아카이빙/압축" → 완료 승격(잔여 1건=파일 일괄처리). framework-openapi=springdoc/Swagger UI 이미 존재 확인(RestDocs 미사용).

### D. 공통 유틸 6종 추가 (framework-core/util, 의존성 0)
- **IoUtils**(가드된 스트리밍: `copyLimited` size-cap·`copyAndSha256` tee·drain/closeQuietly) · **CsvUtils**(RFC4180 write/parse) · **FixedWidthUtils**(바이트 고정폭 전문, CP949) · **CharsetUtils**(MS949/EUC-KR↔UTF-8·decodeLenient) · **TextUtils**(null안전·`truncateByBytes` 한글/이모지 안전 절단·패딩·snake/camel) · **CollectionUtils**(`chunk`·null안전). 전부 순수 정적 JDK. RetryUtils 는 제외(사용자 판단; HTTP 재시도=framework-client). 테스트 `CoreUtilsExtraTest`(@Nested 6). 비자명 로직 독립 재현 10/10 통과.

## 현재 상태 (적용/검증)
- ✅ **사용자 환경에서 YAML 암호화 모듈 컴파일 성공 확인**(직전 메시지). 실제 ENC 키 기동 테스트는 사용자가 나중에.
- ✅ **아카이빙 순수 로직 독립 재현 14/14 통과**(zip-slip 정규화·폭탄 카운팅·실제 `../` zip 탐지·gzip 라운드트립).
- ⚠️ 작성 환경 **JRE-only(javac 없음)+Maven Central 차단** → 두 건 모두 **Spring 부 gradle 컴파일은 미실행**. archtest 5규칙 정적 무충돌(동일 모듈·Jackson0·생성자주입·네이밍). 받는 쪽 검증 경로 아래.
- 신규 파일: 암호화 6(+test2)·`spring.factories`·CryptoProperties/AutoConfig 패치 / 아카이빙 모듈 11(main7+test3+build.gradle)·settings+archtest 와이어링.

## 켜는 법
```bash
# 아카이빙
#   framework.archive.enabled: true   (+ max-entries / max-entry-size / max-total-bytes)
#   Archiver archiver; archiver.zip(List.of(ArchiveEntry.of("a.txt", bytes)), out);
#   archiver.unzip(in, (name, content) -> ...);  // 엔트리 단위 스트리밍
#   archiver.unzipToDirectory(in, targetDir);    // zip-slip 안전
#   archiver.gzip(in, out); archiver.gunzip(in, out);
# 검증(받는 쪽)
./gradlew :framework:framework-archive:test :framework:framework-core:test :framework:framework-archtest:test spotlessApply
```

## 바로 다음 할 일 (Next)
1. **★ 파일 일괄처리(Batch File Processing)** — **다음 세션 최우선(사용자 지정)**. 얇은 모듈 `framework-file-batch`: 여러 파일에 동일 작업(이름변경/이미지변환/압축) 일괄 + 부분실패 격리·결과수집·Java21 가상스레드 병렬·드라이런. image/archive 위임(compileOnly), 신규 의존성 0. **설계서 `docs/NEXT_FILE_BATCH_PROCESSING.md` 그대로 진행.**
2. (devops) **CI 게이트** — `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + 멀티모듈 jacoco 집계.
3. (선택) 아카이빙 후속: **tar/tar.gz**(commons-compress 옵트인)·zip 패스워드. 공통 유틸 잔여=RetryUtils(보류).
4. (devops) 그릇 정비 — 게이트웨이 런타임 점검·k8s 멀티서비스·CI-CD 멀티서비스화.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **AssertJ + 미정 제네릭 모호성**(이번에 컴파일 실패): `assertThat(CollectionUtils.firstOrNull(List.of()))` 처럼 빈 `List.of()` 를 제네릭 메서드에 넘기면 반환 `T` 가 미정이라 `assertThat(Predicate)`/`assertThat(IntPredicate)` 후보가 겹쳐 "reference to assertThat is ambiguous". 해결=명시적 타입 인자(`CollectionUtils.<String>firstOrNull(...)`/`<String>emptyIfNull(null)`).
- **tar 는 JDK 에 없다** — `java.util.zip` 은 zip/gzip/deflate 만. tar/tar.gz 는 commons-compress 가 필요 → "의존성 0" 원칙상 코어는 ZIP+GZIP 만, tar 는 옵트인 후속으로 분리.
- **아카이브는 항상 안전 가드와 함께**: 해제는 zip-slip(`ArchiveSafety.sanitizeEntryName`/`resolveSafely`) + 압축폭탄(엔트리수·엔트리크기·총바이트) 둘 다 필수. `unzip` 콜백에 넘기는 스트림은 `BombGuardInputStream` 으로 감싸 **읽는 도중** 초과 시 예외(헤더 신뢰 금지).
- **스트림 소유권**: archiver 메서드는 호출자 입/출력 스트림을 닫지 않는다(NonClosing 래퍼). 내부 Inflater/Deflater 만 try-with-resources 로 정리.
- **설정값 암호화 토글 기본 on**(일반 3단 토글 off 규약의 의도적 예외 — ENC 없으면 무동작이라 안전, off면 ENC 리터럴이 조용히 샘). EPP 등록=`spring.factories`(framework-core 신설), 마스터키 ENC 불가.
- (지난·유효) Jackson3(`tools.jackson.*`, annotation만 예외) / compileOnly 타입 test 재선언 / 새 오토컨피그 `.imports`+등록가드 / EPP 는 spring.factories(Boot4 패키지) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 필터·EPP 는 GlobalExceptionHandler 밖 / prod 가드(JWT·DevAuth·Password·AES마스터키) / Boot4 패키지 리네임 추측 금지 / Swagger=springdoc(framework-openapi, [선택] opt-in).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증 가능하게.
2. 기존 인터페이스는 capability 인터페이스로 확장(ISP). 생성자 변경 시 기존 오버로드 유지.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`.
4. 새 오토컨피그면 `.imports`+등록가드 테스트. **EPP 는 `spring.factories`**(Boot4 패키지). 신규 모듈은 settings include + archtest testImplementation 도 추가.
5. 오토컨피그 토글 기본 off + `@ConditionalOnMissingBean`(암호화 설정복호화 토글만 예외적 on).
6. 테스트: 핵심 알고리즘 단위(JDK) + 오토컨피그 토글/빈선택/등록가드. EPP 는 `StandardEnvironment`+`MapPropertySource`.
7. 드롭인: 변경 파일 전부 한 zip, 루트 `unzip -o`. 문서 동기화. 사용자 환경 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증.

<!-- 갱신 끝 -->
