# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**직전 = 백로그 3건 마감(2026-06-04): QR 생성 모듈 + SFTP 후속(연결 풀·키 회전) + (devops) CI 게이트/멀티모듈 jacoco 집계.** ① **framework-qr** 신설 — `QrGenerator` SPI + `ZxingQrGenerator`(ZXing `core` 인코딩만, **렌더링은 JDK ImageIO 직접** → `zxing-javase` 불필요·외부 의존성 **1개**), PNG 전용·ECC L/M/Q/H, mfa 의 `otpauth://` URI 를 서버측 QR PNG 로 보완. ② **framework-file-sftp 후속**(둘 다 옵트인·기본 off) — 순수 JDK `BoundedObjectPool<ClientSession>`(connection pool) + `ReloadingSftpCredentialProvider`(key rotation), `SftpFileStorage` 를 자격증명 공급자+`PoolSettings(nullable)` 로 리팩터링. ③ **CI** — 루트 `jacoco-report-aggregation` + 전 39모듈 집계(`testCodeCoverageReport`), GitHub Actions **PR 차단 `verify` 잡** + Jenkinsfile Architecture Rules 스테이지. **순수 로직 검증: QR 22/22 · SFTP 풀+회전 33/33 (JDK 단독 하네스).** **받는 쪽 gradle 전 항목 통과 확인 완료(2026-06-04): `:framework-qr:test`·`:framework-file-sftp:test`·`:framework-archtest:test`·`testCodeCoverageReport`·spotless 모두 ✅.** **바로 다음 = commit/push.** 이후 후보 = 그릇 정비(k8s 멀티서비스·게이트웨이 런타임 점검) 또는 잔여 백로그(tar/tar.gz·규제특화).

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: QR + SFTP 후속 + CI 게이트/jacoco 집계 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)** / ZXing core 3.5.4(신규, BOM 밖) · MINA SSHD 2.16.0

## 직전에 한 것 (Done — 순수 로직 하네스 통과 / 라이브러리 경로는 받는 쪽 대기)
- **① framework-qr 신설(옵트인 신규 모듈)**: `QrGenerator` SPI(`toPng`/`generate(content, QrSpec)`) + `ZxingQrGenerator`(ZXing `core` 인코딩) + `QrSpec`(record+Builder, sizePx/margin/ECC/charset/색/검증) + `QrEccLevel`(L/M/Q/H, zxing-free) + `PixelGrid` 경계 + `QrPngRenderer`(JDK ImageIO, zxing-free) + `QrErrorCode`(QR0001~5) + `QrProperties`/`QrAutoConfiguration`(`@ConditionalOnClass(QRCodeWriter)`+`@ConditionalOnProperty(framework.qr.enabled=true)`+`@ConditionalOnMissingBean`) + `.imports` + README. **렌더링을 JDK ImageIO 로 직접** 해 `zxing-javase` 를 떼고 외부 의존성 1개(`com.google.zxing:core`)로 최소화. PNG 전용(JPEG 스캔성 저하 제외).
- **② framework-file-sftp 후속(연결 풀 + 키 회전, 둘 다 옵트인)**: 신규 `pool/BoundedObjectPool`(제네릭 순수 JDK — cap+maxWait·LIFO 재사용·validate-on-borrow·maxIdle/maxLifetime 만료·invalidate·close 드레인, 훅은 락 밖)·`cred/SftpCredentials`(record, JDK `KeyPair` 만)·`cred/SftpCredentialProvider`(SPI+`fixed`)·`cred/ReloadingSftpCredentialProvider`(지문 mtime+size 변경 감지 재로드·interval 게이트·실패 시 기존 유지)·`SftpKeyLoader`(MINA `FileKeyPairProvider` 위임·지문). `SftpFileStorage` **생성자 시그니처 변경**(자격증명 공급자+`PoolSettings(nullable)`, 풀 null=기존 "작업마다 세션 개폐" 보존). `FileStorageProperties.Sftp` 에 `Pool`/`KeyRotation` nested + `SftpFileStorageAutoConfiguration` 가 토글 따라 공급자/풀 빌드.
- **③ (devops) CI 게이트 + 멀티모듈 jacoco 집계**: 루트 `build.gradle` 에 `jacoco-report-aggregation` 적용 + 전 39모듈 `jacocoAggregation` 나열(framework-qr 포함) → `testCodeCoverageReport`. `deploy/cicd/ci-cd.yml` 에 **PR 차단 `verify` 잡**(spotlessCheck → `:framework-archtest:test` → 전모듈 `test` → 통합 커버리지 → OWASP → push 한정 Sonar), build/docker/deploy 가 그 뒤 의존. `Jenkinsfile` 에 Architecture Rules 스테이지 + 전모듈 test+집계.
- **검증(작성환경, JDK 단독 standalone 하네스 — 실제 소스 컴파일·실행)**: QR 순수(`QrSpec` 검증·`QrPngRenderer` 실 PNG 렌더→ImageIO 디코드 왕복) **22/22**, SFTP 풀+회전(생성/LIFO/cap+timeout/validate/maxIdle/maxLifetime/invalidate/close 드레인/블로킹 핸드오프 + 회전 첫로드/interval 게이트/변경감지/실패유지/재시도) **33/33**. ZXing encode→decode 왕복·MINA 서버 풀 왕복은 JUnit(받는 쪽).
- **문서(5종 + 모듈 README 2종)**: `docs/FRAMEWORK_MODULES.md`(§2.4 dated 마감 + 모듈표 qr/sftp 행) · `STACK.md`(zxing-core 3.5.4 행) · `README.md`(선택 모듈 목록 + 의존성 예시 qr/sftp) · `HANDOFF.md`(§1 sftp/qr 항목·§6 함정 2묶음·§7 dated 저널·우선순위 마킹) · `HANDOFF_SUMMARY.md`(이 문서) · `framework/framework-qr/README.md`·`framework/framework-file-sftp/README.md`(풀/회전 절).

## 새로 확정한 함정 (HANDOFF §6 등록)
- **QR 의존성 1개 원칙**: 렌더링은 JDK ImageIO 직접 → `zxing-javase`(MatrixToImageWriter) 금지(의존성만 늘고 가치 없음). **PNG 전용**(JPEG 손실=스캔성 저하). mfa 의 QR 미생성 정책 유지(별도 옵트인 모듈로 보완).
- **SFTP 풀 훅은 락 밖 호출**: creator/validator/destroyer(네트워크 IO·블로킹)를 락 보유 중 호출하면 풀이 직렬화 → 슬롯만 락 안 예약, 생성/검증/파기는 락 밖. creator 실패 시 예약 슬롯 반납.
- **`ClientSession::isOpen` 유효**: `ClientSession`→`Session`→`SessionContext`→`Closeable extends java.nio.channels.Channel.isOpen()`(MINA 2.x). validate-on-borrow 로 끊긴 세션 대여 직전 폐기.
- **키 회전은 "새 세션부터"**: `openSession()` 이 세션마다 `current()` 해석 → 풀에 살아있는 세션은 옛 키. 즉시 전파 필요 시 `maxLifetime` < 회전 주기. 재로드 실패 시 기존 자격증명 유지+다음 주기 재시도(fail-safe).
- **`SftpFileStorage` 생성자 시그니처 변경** → 기존 테스트 `new SftpFileStorage(...)` 깨짐(RoundTrip/Pooled 는 `SftpCredentialProvider.fixed(SftpCredentials.password(...))`+`PoolSettings(nullable)` 로 갱신). 풀 null=도입 전 경로 보존.
- **jacoco 집계 ≠ Sonar 수집**: `testCodeCoverageReport`(사람용 통합 1장)와 Sonar 의 모듈별 XML 글롭은 **독립**(집계를 Sonar 에 먹이면 이중 합산). 새 모듈은 settings include + 루트 `jacocoAggregation` 한 줄 함께 추가.
- **⚠️ jacoco 집계는 루트에도 BOM import 필요(첫 실행이 잡음)**: `aggregateCodeCoverageReportResults` 는 루트에서 해소되는데 `io.spring.dependency-management` 의 BOM 이 소비자(루트)로 전이 안 돼, gateway 의 버전 없는 spring-cloud 스타터를 못 찾아 `Could not find ...:.`(빈 버전)+config-cache 직렬화 실패. **해법=루트 build.gradle 에 `dependencyManagement { imports { mavenBom boot/cloud/testcontainers } }` 추가**(적용 완료). 모듈 단위 `:X:test` 는 통과하므로 집계 첫 실행 전까지 잠복.
- (작업 환경 — 유효) **Maven Central 차단** → SB4/SS7/zxing/sshd 다운로드 불가 = 이 환경 gradle 빌드/테스트 불가(정적 리뷰 + JDK 단독 하네스로 순수 로직만 검증). GitHub clone/raw 대조 가능.

## 실행/검증 (받는 쪽 — gradle 가능 환경)
```bash
./gradlew :framework:framework-qr:test                       # QR: QrSpec/ZxingQrGenerator(encode→decode 왕복)/AutoConfiguration 토글
./gradlew :framework:framework-file-sftp:test                # SFTP: BoundedObjectPool/ReloadingSftpCredentialProvider/내장 MINA 풀 왕복
./gradlew :framework:framework-archtest:test                 # 아키텍처 규칙(모듈 순환·Jackson2 금지·AutoConfiguration/Properties 규약·필드주입 금지)
./gradlew testCodeCoverageReport                             # 멀티모듈 통합 커버리지(build/reports/jacoco/testCodeCoverageReport/)
./gradlew :framework:framework-qr:spotlessApply :framework:framework-file-sftp:spotlessApply
```
> 작성환경은 Maven Central 차단으로 gradle 실행 불가 → 위 명령은 받는 쪽에서 실행 확인 필요. 순수 로직은 JDK 단독 하네스로 QR 22/22·SFTP 33/33 통과(2026-06-04). **받는 쪽 최종 확인(2026-06-04, 전부 ✅)**: `:framework-qr:test`·`:framework-file-sftp:test`·`:framework-archtest:test`·spotless·`testCodeCoverageReport` 모두 통과. (`testCodeCoverageReport` 는 루트 BOM 미import 로 1차 실패 → 루트 build.gradle 에 BOM import 추가 후 통과.) 남은 건 commit/push.

## 다음 (Next) 후보
- **▶ 받는 쪽 빌드/테스트 통과 확인 후 commit/push** (이번 세션 산출물 = framework-qr 신규 + framework-file-sftp 후속 + CI/jacoco + 문서).
- **그릇 정비**(권장 다음): 게이트웨이 런타임 점검(CORS preflight·rate-limit 429) · k8s redis/secret/멀티서비스 + observability ServiceMonitor 실배포 · CI-CD 멀티서비스화(현재 user-service 단일 → gateway/admin/auth-server 확장).
- (선택 백로그) 아카이빙 tar/tar.gz(commons-compress 옵트인) · RetryUtils · 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 암호화 파일 키 회전 · S3 멀티파트 병렬 업로드(TransferManager).
- (보류) SSO 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn) · OIDC B안 전체 흐름 e2e(confidential demo-rp).
<!-- 갱신 끝 -->
