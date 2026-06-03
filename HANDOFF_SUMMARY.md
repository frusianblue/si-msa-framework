# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**SFTP 원격 저장 모듈 `framework-file-sftp` 완료** — 사용자 추가 요청(QR/OTP/SFTP 점검 중 SFTP 우선). `framework-file` 의 `FileStorage` SPI 에 **SFTP 백엔드**(`storage.type=sftp`)를 `framework-file-s3` 와 동일 구조로 추가. 순수 JDK 에 SSH 가 없어 **Apache MINA SSHD**(`sshd-core`+`sshd-sftp` **2.16.0**, BOM 밖·`implementation` 비노출) 위임. **연결 모델**: `SshClient` 1회 start 후 재사용·**작업마다 세션 개폐**(풀 없음), `load`/`loadRange` 반환 스트림은 `SessionBoundInputStream` 으로 **close 시 세션 함께 정리**. **`RangeReadableStorage` 구현**(skip+`BoundedInputStream` → 컨트롤러 206, S3 동등; presigned 는 SFTP 미적용). 호스트키 **기본 strict**(known_hosts·fail-closed, `false`로 개발 완화+경고)·인증 password/private-key(+passphrase). 순수 경로/Range 헬퍼 JDK 단독 **22/22 통과**, 정식 JUnit 3종(경로 단위·오토컨피그 토글/`FilteredClassLoader` 백오프/.imports·**내장 MINA SFTP 서버 실제 왕복**). 참고: **OTP 는 이미 있음**(`framework-mfa`), **QR 은 의도적으로 otpauth URI 만**(zxing 회피). **다음 세션 최우선 = (devops) CI 게이트 + 멀티모듈 jacoco 집계** 또는 그릇 정비(게이트웨이 런타임/k8s 멀티서비스).

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: SFTP 원격 저장 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### framework-file-sftp 신설 (FileStorage SPI 의 SFTP 백엔드, MINA SSHD 위임)
- **`SftpFileStorage`**(implements `FileStorage`,`RangeReadableStorage`,`AutoCloseable`): 생성자에서 `SshClient.setUpDefaultClient()` + 호스트키 verifier 설정 + `start()`(NIO만, 연결 아님). host/username blank 면 `IllegalStateException`(기동 fail-fast). private-key 는 `FileKeyPairProvider`(+`FilePasswordProvider`)로 기동 시 로드·캐시.
- **연결/세션**: `openSession()` = `client.connect(user,host,port).verify(connectTimeout).getSession()` + password/공개키 identity + `auth().verify(authTimeout)`. `withSftp(fn)` = try-with-resources(session+SftpClient) — store/delete/stat 처럼 호출 내 완결 작업용. `load`/`loadRange` 는 스트림이 세션을 물어야 하므로 `SessionBoundInputStream`(close 시 sftp→session 정리).
- **연산**: store(`newKey`=yyyy/MM/dd/{uuid}.{ext} → `ancestorDirs` mkdir -p → `write(Create,Write,Truncate)` transferTo) · load(`read(Read)` → 세션바운드) · delete(`remove`, `SSH_FX_NO_SUCH_FILE` 무시=멱등) · type()="sftp" · contentLength(`stat().getSize()`) · loadRange(open→`skipFully(start)`→`BoundedInputStream(end-start+1)`→세션바운드). 에러는 `mapSftp`(NO_SUCH_FILE→NOT_FOUND, else INTERNAL_ERROR, 자격증명 미노출).
- **호스트키**: strict=true → `KnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, known_hosts)`(fail-closed) / false → `AcceptAllServerKeyVerifier.INSTANCE`+warn. known_hosts 기본 `~/.ssh/known_hosts`.
- **순수 헬퍼(package-private static, 검증대상)**: `join`(슬래시 정규화·빈 base→홈상대)·`parentOf`·`ancestorDirs`(mkdir -p 대용, 얕은→깊은)·`newKey`·`extOf`·`skipFully`(skip()==0 폴백)·`BoundedInputStream`.
- **autoconfig** `SftpFileStorageAutoConfiguration`: `@AutoConfiguration @ConditionalOnClass(SftpClient.class) @ConditionalOnProperty(storage type=sftp)` + `@Bean @ConditionalOnMissingBean(FileStorage)` → props 의 `Storage.Sftp` 필드로 `SftpFileStorage` 생성. `.imports` 등록.
- **config**: `framework-file` 의 `FileStorageProperties.Storage` 에 **`Sftp` nested 추가**(host/port=22/username/password/privateKeyPath/privateKeyPassphrase/baseDir=""/strictHostKeyChecking=true/knownHostsPath/connectTimeout=10s/authTimeout=10s) + s3 옆 getter/setter.
- **와이어링**: `gradle/libs.versions.toml`(`sshd=2.16.0`) · 루트 `build.gradle` ext(`sshdVersion`) · `settings.gradle` include · `framework-archtest/build.gradle` testImplementation. build.gradle = `api framework-file` + `implementation sshd-core/sshd-sftp`(+test 재선언) + lombok + starter-test.

## 현재 상태 (적용/검증)
- ✅ **순수 경로/Range 헬퍼 JDK 단독 하니스 22/22**(join 6·parentOf 4·ancestorDirs 4·extOf 3·range 10..19/EOF클램프·skipFully 폴백·bounded 단건).
- ✅ **archtest 7규칙 정적 무충돌**(autoconfig=`@AutoConfiguration`·`Sftp`는 nested(top-level *Properties 규칙 비대상)·Jackson0·필드주입0).
- ⚠️ 작성 환경 **Maven Central 차단** → MINA(`org.apache.sshd.*`) 컴파일·JUnit(왕복 포함) 미실행. 받는 쪽 검증 경로 아래. (MINA API 호출은 javadoc/web 으로 시그니처 확인하며 보수적으로 작성 — 가장 위험한 건 내장서버 왕복 테스트라 문제 시 그 파일만 조정 가능.)
- 신규 파일: 모듈 main 2(`SftpFileStorage`·autoconfig)+`.imports`+build.gradle / test 3(Path 단위·AutoConfig 토글·RoundTrip 내장서버) / 변경 5(libs.versions.toml·root build.gradle·settings.gradle·archtest build.gradle·`FileStorageProperties`). **framework-file-batch 는 이미 전달·컴파일 확인됨 → 이번 드롭인 제외.**

## 켜는 법
```bash
# framework.file.storage.type: sftp  (+ sftp.host/username/password 또는 private-key-path)
#   @Autowired FileStorage storage;  // sshd 클래스패스 + type=sftp 면 SftpFileStorage
#   StoredFile sf = storage.store(in, "report.pdf", "application/pdf", size);  // base-dir/yyyy/MM/dd/{uuid}.pdf
#   try (InputStream s = storage.load(sf.storedPath())) { ... }                // 닫을 때 세션도 정리
#   long len = ((RangeReadableStorage) storage).contentLength(sf.storedPath());
# 운영: strict-host-key-checking=true + known_hosts (개발만 false). 인증은 private-key-path 권장.
# 검증(받는 쪽)
./gradlew :framework:framework-file-sftp:test :framework:framework-archtest:test spotlessApply
```

## 바로 다음 할 일 (Next)
1. **(devops) CI 게이트** — `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + 멀티모듈 jacoco 집계(루트 aggregate).
2. **그릇 정비** — 게이트웨이 런타임 점검(CORS preflight·rate-limit 429)·k8s 멀티서비스(redis/secret/ServiceMonitor 실배포)·CI-CD 멀티서비스화.
3. (선택) **QR 코드 이미지 생성** — 현재 mfa 는 otpauth URI 만. zxing(`com.google.zxing:core`) 옵트인(BOM 밖 implementation) 또는 순수 JDK 인코더. **SFTP 후속** — 연결 풀(고처리량)·키 회전.
4. (선택) 아카이빙 후속 tar/tar.gz(commons-compress 옵트인)·RetryUtils·규제특화 잔여(pki/hsm/recon/egov)·saga 단계별 타임아웃/보상 재시도·암호화 파일 키 회전.
   - 기본기능 카탈로그 §6 대기열 잔여 = 서버측 S3 멀티파트 병렬 업로드(백로그)뿐. 추적은 `docs/BASELINE_FEATURES.md`.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **SFTP 는 연결 상태형(S3 무상태와 다름)**: `SshClient` 는 start 후 재사용(start=NIO만, 연결 아님 → 더미 host 로 빈 생성 OK), **작업마다 세션 개폐**(풀 없음). `load`/`loadRange` 스트림은 세션을 물어야 하므로 `withSftp` 로 닫으면 안 되고 `SessionBoundInputStream` 으로 스트림 close 시 세션 정리 — **호출자는 반드시 스트림을 닫아야 누수 없음**.
- **loadRange 는 handle-offset 대신 skip+bounded**: open InputStream → `skipFully(start)`(skip()==0 시 read 폐기 폴백) → `BoundedInputStream(len)`. mkdir -p 는 `ancestorDirs` stat-후-mkdir(`ALREADY_EXISTS` 무시), 없는 파일 delete 는 `SSH_FX_NO_SUCH_FILE` 무시(멱등).
- **호스트키 기본 strict = fail-closed**: known_hosts 없으면 거부(`KnownHostsServerKeyVerifier(RejectAll,…)`). `strict-host-key-checking=false` 는 `AcceptAll`+경고(개발 전용, 운영 금지).
- **MINA 의존성**: `sshd-core`+`sshd-sftp` **동일 버전**, **3.0.0 은 마일스톤(2.x API 비호환) → 2.16.0 고정**(BOM 밖, 카탈로그 sshd + 루트 ext). 타입은 `implementation`(전이 금지, S3 와 달리 SshClient 빈 미노출 — 깊은 커스터마이즈는 앱이 `FileStorage` 빈 직접 정의).
- **내장 SFTP 서버 테스트**: `setUpDefaultServer`+`SimpleGeneratorHostKeyProvider`(인메모리)+`PasswordAuthenticator`+`SftpSubsystemFactory`+`VirtualFileSystemFactory(@TempDir)`. `setSubsystemFactories` 는 `List<SubsystemFactory>`(불변 제네릭)이라 명시적 타입 리스트 사용. 클라는 strict=false(서버 호스트키 임의생성).
- (지난·유효) Jackson3(`tools.jackson.*`, annotation만 예외) / compileOnly·implementation 타입 test 재선언(introspection) / 새 오토컨피그 `.imports`+등록가드 / EPP 는 spring.factories(Boot4 패키지) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 필터·EPP 는 GlobalExceptionHandler 밖 / prod 가드(JWT·DevAuth·Password·AES마스터키) / Boot4 패키지 리네임 추측 금지 / 설정값 암호화 토글만 예외적 기본 on.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring/라이브러리 무의존 코어로 분리해 JDK 단독 검증(이번 path/Range 헬퍼가 그 예 — 라이브러리 차단 환경에서 가장 안전한 검증).
2. 기존 인터페이스는 capability 인터페이스로 확장(ISP — 이번 SFTP 가 `RangeReadableStorage` 구현). 생성자 변경 시 기존 오버로드 유지.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), **BOM 밖=`implementation`**(이번 sshd) + 카탈로그 버전 + 루트 ext 브리지.
4. 새 오토컨피그면 `.imports`+등록가드(또는 토글 스모크). 신규 모듈은 settings include + archtest testImplementation 도 추가.
5. 오토컨피그 토글/`@ConditionalOnProperty`(type 등) + `@ConditionalOnMissingBean` + 라이브러리 `@ConditionalOnClass` 백오프.
6. 테스트: 핵심 알고리즘 단위(JDK) + 오토컨피그 토글/빈선택/`FilteredClassLoader` 백오프 + (가능하면) **내장 서버 실제 왕복**(이번 MINA SshServer).
7. 드롭인: 신규+변경 파일 한 zip, 루트 `unzip -o`. 문서 동기화(README/STACK/FRAMEWORK_MODULES/HANDOFF/HANDOFF_SUMMARY/BASELINE). 사용자 환경 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증.
<!-- 갱신 끝 -->
