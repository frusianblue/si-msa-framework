# FRAMEWORK_MODULES.md — 전 기능 On/Off 모듈 설계서 (금융 우선)

> 목적: 프레임워크의 **모든 기능을 일관된 규약으로 켜고 끄도록** 모듈을 분해하고,
> 공공·금융·일반 SI를 같은 코어로 커버한다. 기존 저장소의 오토컨피그 패턴(`@AutoConfiguration` + `.imports` +
> `@ConditionalOnProperty` + `@ConditionalOnMissingBean`)을 그대로 확장한다.
> 기준일: 2026-05-31

---

## 0. 진행 현황 (2026-06-03)
- ✅ **토대**: framework-idempotency · framework-i18n · framework-idgen · framework-client (선택형, 3단 토글 적용)
- ✅ **보안 완성(ISMS-P)**: framework-security 확장(비번 만료/이력·동시로그인) · framework-audit(접속/감사 로그 적재·조회, logging|jdbc|kafka)
- ✅ **framework-secure-web**: 보안헤더·경로조작 차단·인젝션 스크리닝·CSRF 더블서브밋(필터 계층, XSS 본문은 core)
- ✅ **금융 핵심**: framework-datasource(읽기/쓰기 분리 라우팅 + 독립 다중 DB) · framework-messaging(Transactional Outbox + Kafka 릴레이 **+ 소비자측 멱등 소비**, `x-event-id`↔framework-idempotency) · audit↔messaging 연동(`store.type=kafka`)
- ✅ **업무 생산성**: framework-excel(POI 스트리밍/양식검증) · framework-batch(Batch6+Quartz) · framework-task(Spring Cloud Task 5.0.1 실행이력, Boot4 네이티브 — Batch6 조합 실투입 예제 `examples/batch-task-reference/` + 실무 패턴 5종 `examples/batch-cookbook/` + 처리방식·DB유형 카탈로그 6종 `examples/batch-types/`) · framework-notification(메일/SMS/알림톡) · framework-pdf(PDF 산출물·한글 TTF 임베딩, OpenPDF) · framework-image(리사이즈/썸네일·EXIF 보정·메타 제거, ImageIO·의존성 0)
- ✅ **규제특화 시작**: framework-mfa(2단계 인증 — TOTP/OTP + ISMS-P 복구코드, **외부 의존성 0개**, security 로그인 흐름에 `MfaGate` SPI 로 연결)
- ✅ **소셜 로그인(2026-06-03)**: **framework-oauth-client 신설** — OAuth 2.0/OIDC 인가코드 흐름으로 외부 IdP(google/kakao/naver 프리셋) 신원확인 후 **security 의 JwtProvider/TokenStore 로 자체 JWT 발급**. 앱은 `OAuthUserResolver`(외부신원→`AuthenticatedUser` 매핑/JIT가입) 1개만 구현(자체로그인 `Authenticator` 패턴과 대칭). state(CSRF) memory|redis(다중파드 redis), 발급기 `OAuthTokenIssuer` 교체 시 LoginService 통합 가능. 외부호출 RestClient·JSON 파싱 **Jackson 3(tools.jackson)**, **새 외부 의존성 0개**(web/redis=compileOnly). 순수 로직(프리셋·중첩속성·state 1회소비) 단위테스트 + 오토컨피그 3단 토글 테스트. 사용 가이드 `docs/modules/OAUTH_CLIENT.md`. ⚠️ 작성환경 Maven 차단 → 받는 쪽 `:framework:framework-oauth-client:test`+`spotlessApply` 확인.
- ✅ **OIDC RP 강화(2026-06-03, framework-oauth-client 확장)**: per-provider `oidc.enabled`(기본 off — kakao/naver 등 비OIDC 흐름 보존). 켜면 callback 에서 **id_token 검증**(JWKS 의 RSA/EC 서명 또는 HS=client-secret HMAC · iss · aud⊇client-id · exp/nbf±clock-skew · nonce · sub) + **discovery 자동적용**(issuer/discovery-uri→authorization/token/userinfo/jwks 보충, 지연·1회·캐시) + **nonce 바인딩**(authorize↔callback, state 와 함께 저장). 신원은 검증된 id_token 클레임(userInfoUri 있으면 빈 필드만 userinfo 보충). 신규 `oidc/` 4종(`OidcDiscoveryClient`·`JwksKeyResolver` 캐시+회전 재조회+쿨다운·`IdTokenVerifier`·`OidcMetadataResolver`) + 기존 8파일 수정. id_token 검증은 **jjwt 재사용**(security `api` 전이, 테스트용 impl/jackson 만 `testRuntimeOnly`). **새 외부 의존성 0**. **사용자 환경 컴파일 + 26 테스트 통과 확인.** 사용 가이드 `docs/modules/OIDC_HARDENING.md`.
- ✅ **SAML 2.0 SP(2026-06-04)**: **framework-saml-sp 신설** — SAML 만 말하는 외부/사내 IdP(공공 통합인증·Keycloak/Azure AD SAML 모드)에 **SP 로 연동** → IdP 메타데이터 기반 등록(`RelyingPartyRegistrations.fromMetadataLocation`) → ACS 성공 시 **서버 세션 없이 즉시 자체 JWT 발급**(수기 JSON, Jackson 비의존). 앱은 `SamlUserResolver`(외부신원→`AuthenticatedUser`) 1개만 구현(OAuth `OAuthUserResolver`·자체 `Authenticator` 와 대칭). 발급기 `SamlTokenIssuer`(security `JwtProvider`/`TokenStore` 재사용, LoginService 위임 교체 가능). 전용 `SecurityFilterChain`(`/saml2/**`,`/login/saml2/**` 매처 + 높은 우선순위, `@AutoConfiguration(after=SecurityAutoConfiguration)`)으로 **framework-security 무수정**. ⚠️ **이 프레임워크 최초의 "새 외부 의존성 0" 예외**: `spring-security-saml2-service-provider` 가 OpenSAML 을 전이로 끌어오고(버전=SS 관리, 명시 핀 금지) **OpenSAML 4+ 는 Central 밖 → 루트에 Shibboleth 저장소(그룹 한정) 추가 필수**. 멀티 파드는 현재 게이트웨이 스티키 세션(redis AuthnRequest 저장소는 다음 단계 — `request-repository: redis` 설정 시 fail-fast). 순수 매핑 로직(`SamlAttributeMapper`: friendly/OID 후보, 설정키 우선, 다중값/null 안전)은 **JDK 단독 14케이스 실행검증** + 오토컨피그 토글/백오프 가드 테스트. **✅ 받는 쪽 환경 BUILD SUCCESSFUL + 컴파일 정상 확인(2026-06-04)** — SAML 본체·체인·OpenSAML(Shibboleth `5.1.6`)·`spring-security-saml2-service-provider:7.0.5` 정상 해소. deprecation 경고 1건(`Saml2AuthenticatedPrincipal`, SS7→`Saml2AssertionAuthentication`+`Saml2ResponseAssertionAccessor`)은 메서드 한정 `@SuppressWarnings`+TODO(7.0.x 동작·제거 빨라야 SS8). 사용 가이드 `docs/modules/SAML_SP.md`, 설계/후속 `docs/NEXT_SSO.md` §5·§6.
- ✅ **SAML IdP-initiated SLO 수신(2026-06-04, 6.2-A)**: **framework-saml-sp 확장** — 외부 IdP 중앙 로그아웃(IdP-initiated SLO) 시 우리 JWT 도 무효화해 "중앙 로그아웃 준수" 달성. `SamlLogoutInfo`(registrationId/nameId/sessionIndexes) + `SamlLogoutUserResolver` SPI(NameID→우리 userId 역매핑, `SamlUserResolver` 대칭, 미매칭=null graceful) + `SamlSessionTerminator`(LoginService 결합 분리, SAML 전용 앱 확장점) + `SamlSloService`(무상태 오케스트레이션, slf4j 만 의존) + `SamlSloLogoutHandler`(SS SLO 필터가 검증 후 호출하는 `LogoutHandler` 브리지). `framework-security` 에 `LoginService.logoutAllByUserId(userId, clientIp, reason)` 신설(access token 없이 userId 로 전 세션 무효화 — 관리자 강제 로그아웃·계정도용에도 재사용). 토글 `framework.saml-sp.slo.enabled=false`(기본); 켜면 체인에 `/logout/saml2/**` + `saml2Logout` + 핸들러. ⚠️ **SAML 본체(서명검증·XML·LogoutResponse 생성)는 SS `saml2Logout` 기본구현에 위임 → 우리 기여물 OpenSAML 무의존**(6.1 코덱과 동일 분리). **함정**: ① SS `saml2Logout` 은 세션결합(SP-initiated 는 `Saml2Authentication` 의존) → IdP-initiated 만 `{registrationId}` URL 경로로 무상태 수신(#10820) ② SS 가 파싱된 NameID 를 핸들러에 안 넘김 → 완전 무상태 NameID-XML 추출은 OpenSAML 디코더 확장점 ③ `LoginService` 는 `@ConditionalOnBean(Authenticator)` → SAML 전용 앱엔 없을 수 있어 `SamlSessionTerminator` 로 분리 ④ 토큰 무효화 완전 커버는 `concurrent-session.enabled=true`+공유 TokenStore(redis) 전제(레지스트리 열거; 없으면 0건). 순수 로직(`SamlSloService` 분기 + `registrationIdFromUri`) **JDK 단독 15케이스 실행검증** + JUnit(SamlSloService/SamlSloLogoutHandler). 본체 라운드트립은 받는 쪽. 설계 `docs/NEXT_SSO.md` §6.2-A, 사용 `docs/modules/SAML_SP.md` §7. (다음=6.2-B SP-initiated.)
- ✅ **게이트웨이 엣지 인증(2026-06-03)**: services/gateway 에 WebFlux `GlobalFilter` 추가 — 화이트리스트 외 경로의 Bearer JWT 를 게이트웨이에서 1차 검증(서명+만료+typ) 후 신뢰헤더 `X-User-Id`/`X-User-Roles` 주입(클라이언트 위조헤더는 항상 제거=스푸핑 차단), 다운스트림 framework-context 가 사용. security(서블릿) 충돌 회피 위해 **jjwt 직접 의존**(같은 secret `JWT_SECRET` 공유). 검증 userId 로 레이트리밋 사용자 단위化(기존 IP 강등 해소). `gateway.auth.enabled` 기본 off. `GatewayTokenVerifierTest`. ⚠️ 게이트웨이 런타임 점검 보류 → 받는 쪽 `:services:gateway:compileJava :services:gateway:test`. 사용법 `docs/modules/GATEWAY_EDGE_AUTH.md`.
- ✅ **SSO 중앙 로그아웃 / logout-all(2026-06-03, SSO A)**: 게이트웨이가 jti(`Claims.getId()`) 추출 후 `RedisGatewayTokenBlacklist`(`bl:{jti}` reactive 조회, 키 prefix=`RedisTokenStore` 동일)로 **로그아웃된 토큰을 엣지 401 차단**(`gateway.auth.blacklist-check.enabled` 기본 off, redis 전용·fail-fast). `LoginService.logoutAll(access,ip)` = 현재 토큰 항상 블랙리스트(안전망) + `ConcurrentSessionService` 순회로 전 세션 무효, `POST /api/v1/auth/logout-all`(완전 커버는 concurrent-session on + token-store redis). 신규 게이트웨이 `GatewayTokenBlacklist`/`Redis*`/`NoOp*` + verifier/filter/properties/config 수정. 문서 `docs/modules/SSO_CENTRAL_LOGOUT.md`.
- ✅ **운영/관측**: framework-observability(공통 메트릭 태그 `MeterRegistryCustomizer` · Boot4 네이티브 구조화 JSON 로그 · 메트릭/트레이스 OTLP 익스포터 표준, 전부 토글·기본 off). **외부 의존성 0개**(레지스트리/익스포터는 호스트 runtimeOnly opt-in, Boot BOM 관리). k8s 샘플 `deploy/k8s/observability.yaml`
- ✅ **SI 공통 유틸 보강(2026-06-02)**: `framework-core/util` 에 검증(`KoreanRegNoUtils`·`ValidationUtils`)·날짜/영업일(`DateUtils`·`HolidayUtils`)·금액(`MoneyUtils`)·한글(`HangulUtils`)·해시(`HashUtils`)·JSON(`JsonUtils`) 신규 + `MaskingUtils` 확장. 빈/오토컨피그 없는 순수 정적, **새 외부 의존성 0**(JSON 만 Jackson 3). 회귀 테스트 `CoreUtilsTest`. + **빌드 인프라 픽스**: 루트 `subprojects` 에 `testRuntimeOnly junit-platform-launcher` 추가(Gradle 9 에서 테스트 발견 단계 실패 방지, 전 모듈 공통).
- ✅ **멱등성 확장(2026-06-03)**: framework-idempotency 에 **JDBC 스토어**(`store.type=jdbc`, 영속·다중 인스턴스 공유, DDL `db/idempotency-postgres.sql`) + **응답 재생(replay) 모드**(`replay.enabled`, 중복 시 409 대신 저장 응답 재생, 기본 off=하위호환). **외부 의존성 0개**(jdbc/web=compileOnly, H2=test-scope). 코덱·선점·재생 분기 순수 JDK 실행검증.
- ✅ **CORS/Rate-Limit(2026-06-03)**: 게이트웨이=전역 1선(globalcors + Redis RequestRateLimiter), framework-secure-web=직접 노출 서비스의 2선(Spring CorsFilter 옵트인 + 파드-로컬 토큰버킷). 게이트웨이 빌드 통과(런타임 점검 보류).
- ✅ **분산 트랜잭션 오케스트레이션(2026-06-03)**: **framework-saga 신설** — 경량 오케스트레이션 엔진(중앙 상태 + 역순 보상). 단계 커맨드/리플라이는 **기존 messaging Outbox 재사용**(상태변경과 한 트랜잭션=원자적), 상태는 **JDBC 영속**(DDL `db/saga/saga-postgres.sql`), **스턱/재기동 복구 폴러**(`FOR UPDATE SKIP LOCKED`, 옵트인). 전송·멱등 소비는 messaging 담당, 본 모듈은 오케스트레이션만 더함. 상태머신 순수 JDK **15/15** 실행검증, gradle 컴파일 통과(this-escape 경고 1건 수정). **새 외부 의존성 0**(kafka/jdbc=compileOnly, Boot BOM 관리).
- ✅ **테스트 커버리지 전 모듈 확대(2026-06-03)**: 무테스트 라이브러리 모듈 14개(audit·batch·commoncode·excel·file·file-s3·i18n·idgen·messaging·mybatis·notification·openapi·redis·secure-web)에 **오토컨피그 로딩/토글 스모크**(`ApplicationContextRunner`, 서블릿 한정 `WebApplicationContextRunner`) 추가 — `framework.<X>.enabled` 토글을 빈 등록 유무로 검증. introspection 함정에 맞춰 compileOnly 타입(중첩 @Configuration/@Bean 반환 포함) test 재선언(audit=jdbc+web+messaging 등). commoncode/file 은 `@MapperScan`+MyBatis 결합이라 **disabled 백오프**까지(enabled 풀 와이어링은 DB 슬라이스). **신규 테스트 14 + build.gradle 7, 전부 testImplementation → 런타임/배포 영향 0**, 사용자 환경 BUILD 통과. 무테스트 라이브러리 모듈 0. (발견: redis 의 `RedisLoginAttemptAutoConfiguration` 이 `.imports` 미등록 — 다음 후보.)
- ✅ **파일 일괄처리(2026-06-03)**: **framework-file-batch 신설** — 기본기능 대기열 마지막 항목. "같은 작업을 여러 파일에 한꺼번에"를 감싸는 **얇은 오케스트레이션**. `BatchFileOperation` SPI + `FileBatchProcessor`(Spring 무의존): (1)**부분실패 격리**(`continueOnError` 기본, fail-fast 옵션 시 이후 SKIPPED) (2)**Java21 가상스레드**(`newVirtualThreadPerTaskExecutor`)+`Semaphore` 동시도 상한 (3)**드라이런**(IO 0·계획만) (4)**입력순서 보존**(완료순 무관 인덱스 정렬). 교차검증은 `BatchPreflight` capability(개별 apply 가 못 보는 **이름 충돌**을 IO 전에 일괄 검출). 작업 3종: `RenameOperation`(prefix/suffix/regex/sequence/template + 충돌 FAIL/SUFFIX, 인덱스 기반 연번이라 병렬에서도 결정적)·`ImageTransformOperation`(framework-image `ImageProcessor` 위임)·`CompressOperation`(framework-archive `Archiver` 파일별 gzip 위임). image/archive 는 `compileOnly`+**중첩 @Configuration `@ConditionalOnClass`/`@ConditionalOnBean` 백오프**(없으면 그 op 팩토리만 빠지고 rename·오케스트레이터는 동작). 결과 이름은 `BatchSafety`(경로구분자/`..`/드라이브 거부)로 단일 파일명 강제. **외부 의존성 0개.** 순수 로직 JDK 단독 하니스 **27/27** + 위임(실 `ZipArchiver` gzip 라운드트립·모의 `ImageProcessor`) **10/10** 통과, 정식 JUnit 5종(Rename·Processor·Compress·ImageTransform·AutoConfig 백오프) 추가, archtest 7규칙 정적 무충돌. ⚠️ 작성환경 Maven 차단 → Spring 부 gradle 컴파일 미실행, 받는 쪽 `:framework:framework-file-batch:test`+`:framework-archtest:test`+`spotlessApply` 확인. 설계서 `docs/archive/NEXT_FILE_BATCH_PROCESSING.md`.
- ✅ **SFTP 원격 저장소(2026-06-03)**: **framework-file-sftp 신설** — `framework-file` 의 `FileStorage` SPI 에 SFTP 백엔드 추가(`storage.type=sftp`). 순수 JDK SSH 가 없어 **Apache MINA SSHD**(`sshd-core`+`sshd-sftp` 2.16.0, BOM 밖·`implementation` 비노출) 위임. `SshClient` 1회 start 후 재사용·**작업마다 세션 개폐**(풀 없음, stale 연결 회피), `load`/`loadRange` 반환 스트림은 세션을 물고 있다가 close 시 함께 정리(`SessionBoundInputStream`). **`RangeReadableStorage` 구현**(SFTP 임의 오프셋 읽기 → 컨트롤러 206 그대로 활용, S3 와 동등). 호스트키 검증 **기본 strict**(known_hosts 없으면 거부=fail-closed, `strict-host-key-checking=false` 로 개발 시 완화+경고), 인증 password/private-key(+passphrase). 키 `yyyy/MM/dd/{uuid}.{ext}`, mkdir -p 자동, 없는 파일 삭제 멱등. 순수 경로/Range 로직 JDK 단독 **22/22** 통과, 정식 JUnit 3종(경로 단위·오토컨피그 토글/`FilteredClassLoader` 백오프/.imports·**내장 MINA SFTP 서버 실제 왕복** store/load/delete/range). 3.0.0 은 마일스톤(API 비호환)이라 안정 2.x 고정. **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)** — 잔여는 받는 쪽 `:framework:framework-file-sftp:test`(내장 MINA SFTP 서버 왕복)+`spotlessApply` 실행뿐.
- ✅ **QR 생성 / SFTP 후속 / CI·커버리지(2026-06-04)**: 백로그 3건 마감.
  ① **framework-qr 신설** — QR 이미지 생성(옵트인). framework-mfa 가 의존성 회피로 otpauth:// URI 만 주던 것을 서버측 QR PNG 로 보완. `QrGenerator` SPI + `ZxingQrGenerator`(ZXing `core` 인코딩만; **렌더링은 JDK ImageIO 직접** → `zxing-javase` 불필요, 외부 의존성 **1개**). `BitMatrix`→`PixelGrid` 경계로 렌더러는 ZXing 무의존(`QrPngRenderer`). PNG 전용(JPEG 손실=스캔성 저하 제외), ECC L/M/Q/H, 크기/마진/색/charset/최대길이 토글. 순수 로직(`QrSpec` 검증·`QrPngRenderer` 실 PNG 렌더→ImageIO 디코드 왕복) JDK 단독 **22/22**, ZXing 인코딩 경로는 받는 쪽 encode→decode 왕복 JUnit. 카탈로그 2.4, 사용 `framework/framework-qr/README.md`.
  ② **SFTP 후속(연결 풀 + 키 회전)** — framework-file-sftp 확장(둘 다 옵트인, 기본 off=기존 "작업마다 세션 개폐"). **연결 풀**: 순수 JDK `BoundedObjectPool<ClientSession>`(cap+maxWait 대기·LIFO 재사용·**validate-on-borrow**(`isOpen`)·maxIdle/maxLifetime 만료·invalidate·close 드레인). **키 회전**: `SftpCredentialProvider` SPI + `ReloadingSftpCredentialProvider`(파일 mtime+size 지문 변경 감지 재로드, check-interval 게이트, **재로드 실패 시 기존 자격증명 유지·다음 주기 재시도**) — 매 세션 생성 시 `current()` 해석이라 **새 세션부터** 새 키 인증, 기존 세션은 풀 `maxLifetime` 으로 점진 교체. `SftpFileStorage` 가 자격증명 공급자+`PoolSettings(nullable)` 기반으로 리팩터링(생성자 시그니처 변경, 풀 null=기존 경로 보존). 설정 `framework.file.storage.sftp.pool.*`(max-total/max-wait/max-idle/max-lifetime)·`.key-rotation.*`(check-interval). 순수 풀+회전 로직 JDK 단독 **33/33** + JUnit 3종(`BoundedObjectPool`/`ReloadingSftpCredentialProvider`/`SftpCredentials`) + **내장 MINA 서버 풀 왕복**(순차 재사용·스레드 4개 동시) 테스트.
  ③ **(devops) CI 게이트 + 멀티모듈 jacoco 집계** — 루트에 `jacoco-report-aggregation` 적용 + 전 39모듈 `jacocoAggregation` 나열 → `./gradlew testCodeCoverageReport` 단일 통합 리포트(`build/reports/jacoco/testCodeCoverageReport/`; Sonar 의 모듈별 XML 글롭과 독립=이중합산 방지). GitHub Actions 에 **PR 차단 `verify` 잡** 신설(spotlessCheck → `:framework-archtest:test`(아키텍처 규칙) → 전모듈 `test` → 통합 커버리지 → OWASP → push 한정 Sonar), build/docker/deploy 가 그 뒤로 의존. Jenkinsfile 도 동일(Architecture Rules 스테이지 + 전모듈 test+집계). ⚠️ 작성환경 Maven 차단 → 받는 쪽 `./gradlew spotlessApply`·`:framework:framework-qr:test`·`:framework:framework-file-sftp:test`·`testCodeCoverageReport` 실행 확인.
- ✅ **Spring Cloud Task + Batch 조합 실투입화(2026-06-06)**: **framework-task 신설** — 한 번 실행 후 종료하는 작업(배치·마이그레이션·k8s CronJob)의 실행이력을 표준화. `@EnableFrameworkTask`(=SCT `@EnableTask` 메타합성) + `FrameworkTaskExecutionListener`(시작·종료·종료코드·파라미터 표준 감사로깅). `@AutoConfiguration(afterName=SimpleTaskAutoConfiguration)` + `@ConditionalOnClass(TaskExecutionListener)` + `framework.task.enabled`(기본 off) + `@ConditionalOnBean(TaskRepository)`. **framework-batch(Batch6)와 결합** 시 SCT 의 `TaskBatchAutoConfiguration` 이 Job↔Task 를 자동 연결(`TASK_TASK_BATCH`). ⚠️ **SCDF 서버 본체 = OSS EOL**(2025-04 Broadcom 중단, `spring-attic` 아카이브 — 마지막 OSS 2.11.x/Boot2·3, 이후 Tanzu 상용). **Task 라이브러리(5.0.1)만 Boot4 네이티브로 생존**, 메인 Oakwood BOM 밖 → `org.springframework.cloud:spring-cloud-task-dependencies:5.0.1` 별도 BOM(starter 는 stream 바인더까지 끌어와 의도적 미사용, core+batch 만). **실투입 레퍼런스 앱 `examples/batch-task-reference/`**(정산 청크잡: JdbcCursor리더→수수료처리→JdbcBatch라이터 beanMapped, Flyway 로 SCT/Batch6/도메인 DDL 선반영, Dockerfile 종료코드 전파 + k8s CronJob `concurrencyPolicy=Forbid`/`restartPolicy=Never`). 검증: SCT 5.0.1 소스(sparse checkout) + Batch 6.0.3 DDL/패키지(raw) 실측. ⚠️ **Batch6 인프라 아이템 패키지 대규모 재배치** `org.springframework.batch.infrastructure.item.*`(5.x `...batch.item.*` 아님; core 의 Job/Step/builder 는 `...batch.core.*` 그대로). **스케줄링/모니터링 UI** 가이드 `docs/guide/BATCH_SCHEDULING_AND_UI.md`(두 실행모델 + Quartz UI 정확매핑: **QuartzDesk=상용**(OSS 아님, Lite 만 무료)·**`fabioformosa/quartz-manager`=OSS**(Boot4 호환, 본 스택 후보)·.NET 전용 도구 부적합 — UI 는 framework-batch 의 Quartz 모델에만 귀속되며 **JDBC JobStore+클러스터** 전제). ⚠️ 작성환경 Maven 차단 → 받는 쪽 `:framework:framework-task:test`·예제 독립빌드(`examples/batch-task-reference` 는 루트 settings.gradle 밖, mavenLocal 의 `com.company:framework-task:1.0.0` 소비)·`spotlessApply` 확인.
- ⏭️ **다음 후보**: **인증 로드맵 3) SSO — B-SAML + 6.1 redis AuthnRequest + 6.2-A IdP-initiated SLO + 6.3 Authorization Server(별도 `services/auth-server`) 완료(전부 2026-06-04, 6.3 은 받는 쪽 실기동 검증)** **§6.3 후속 전부 완료(2026-06-04)**: ~~리소스 서버 이중 issuer 정합(게이트웨이 `iss` 분기 + AS jwks 검증)~~ ✅ · ~~서명키 회전 스케줄러(framework-lock 리더선출 + 개인키 암호화)~~ ✅ · ~~토큰 발급 라운드트립 통합테스트~~ ✅(4/4) · ~~**OIDC id_token 발급**~~ ✅(`OidcIdTokenIssuanceTest` 2/2, 근본원인=커스텀 provider 의 `FactorGrantedAuthority` 누락). → **다음 = RP 연계(OIDC 풀루프 마감)** — AS 발급 id_token 을 `framework-oauth-client` `IdTokenVerifier` 로 검증하는 e2e(착수 문서 `docs/NEXT_RP_IDTOKEN_LINK.md`). 보류: **6.2-B** SP-initiated SLO · **6.4** Passwordless(WebAuthn). ~~(devops) CI 게이트 + 멀티모듈 jacoco 집계~~ ✅(2026-06-04) · **그릇 정비**(게이트웨이 런타임 점검·k8s 멀티서비스/CI-CD) · (선택) ~~QR 코드(zxing opt-in)~~ ✅·~~SFTP 후속(연결 풀·키 회전)~~ ✅(둘 다 2026-06-04)·아카이빙 tar/tar.gz·규제특화 잔여(pki/hsm/recon/egov). **(문서 정리 2026-06-04: 루트 중복/고아 4종 삭제 + 완료 설계서 `docs/archive/` 이동 — 정본 구조는 HANDOFF §7 저널 참조.)**
- ✅ **독립 다중 DB 완료(2026-06-03)**: framework-datasource 에 `multi.*` 추가 — 서로 다른 물리 DB 마다 `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager` 세트를 `ImportBeanDefinitionRegistrar` 로 동적 등록. `@MapperScan(sqlSessionFactoryRef)`/`@Transactional("<k>TransactionManager")` 는 앱이 배선. 라우팅과 **상호 배타**(fail-fast). 새 외부 의존성 0. 분산 원자성은 여전히 XA 대신 Outbox/Saga로.
- ✅ **분산 락 / 스케줄러 리더 선출(2026-06-03)**: **framework-lock 신설** — `DistributedLock` SPI(소유자 토큰 리스 기반 `tryLock/unlock/keepUntil` + 편의 `runIfLocked`), 백엔드 memory(단일JVM·기본)|redis(`SET NX PX` + Lua CAS)|jdbc(PK INSERT 충돌 선점·만료 재획득, idempotency JDBC 패턴 복제, DDL `db/lock-postgres.sql`). **`@SchedulerLock` 애스펙트**로 k8s 다중 파드 `@Scheduled` 중복 실행 방지(ShedLock 동등 `atMostFor`/`atLeastFor`, atLeastFor 는 조기종료 시 `keepUntil` 로 구현, 네이티브). batch(Quartz job-store 클러스터링)와 구분=평범한 `@Scheduled` 갭. **외부 의존성 0개**(redis/jdbc=compileOnly, H2=test-scope). ⚠️ 작성환경 컴파일 미검증 → 받는 쪽 `:framework:framework-lock:test`+`spotlessApply` 확인.
- ✅ **PDF 산출물 생성(2026-06-03)**: **framework-pdf 신설** — 표 기반 `PdfReport`/`PdfColumn` 스펙 → `PdfExporter` 가 OutputStream 스트리밍(거래내역서/통지서). 한글 **TTF IDENTITY_H 임베딩**(`PdfFontProvider`, 폰트 미설정/깨진바이트는 라틴 폴백·생성 성공), 표 헤더 페이지 반복 + 하단 페이지번호, A4/A5/LETTER/LEGAL·가로·여백·폰트크기 토글. 엔진 **OpenPDF 2.0.2**(iText4 LGPL/MPL fork·AGPL 회피, `com.lowagie.text` 패키지; **3.0+ 는 `org.openpdf` 리네임이라 2.x 고정**; BOM 밖 → 카탈로그+ext 고정·`implementation` 비노출). 3단 토글+레지스트레이션 가드. **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)**.
- ✅ **분산 캐시(2026-06-03)**: **framework-cache-redis 신설** — k8s 다중 파드 공유 캐시. core 의 로컬 Caffeine `CacheManager` 를 Redis 로 **대체**(`@AutoConfiguration(before=CacheAutoConfiguration.class)` 로 core 가 `@ConditionalOnMissingBean` 백오프하도록 먼저 등록). 값=**JDK 직렬화**(`RedisSerializer.java()`, Jackson2 직렬화기 회피)·키=String, 기본 TTL/keyPrefix/null정책 + 캐시별 `ttls`. JSON 직렬화 필요 시 앱이 `RedisCacheConfiguration` 빈 직접 등록(우선). 끄면(기본) core Caffeine. 3단 토글+레지스트레이션 가드. **외부 의존성 0개**(spring-data-redis=compileOnly+test, Boot BOM). **사용자 환경 빌드 검증 완료(2026-06-03)** — `:framework:framework-cache-redis:test` 이상 없음.
- ✅ **개인정보 로그 마스킹(2026-06-03)**: **framework-log-masking 신설** — core `MaskingUtils`(값 단위)의 보완으로 **자유 텍스트 로그**에 섞인 PII 를 정규식 탐지 후 `MaskingUtils` 형식에 위임(전사 일관). 두 경로: **(1)** `SensitiveDataMasker` 빈 명시 호출(구조화 로그까지, 1차) **(2)** Logback `%mmsg` 컨버터(`MaskingMessageConverter`+정적 다리 `MaskingSupport` 폴백+`LogMaskingInstaller`, 패턴 로그 자동, 2차 방어망). 내장 규칙 RRN/카드/휴대폰/이메일(계좌는 오탐 위험 기본 off)+커스텀 정규식. 순수 엔진은 Spring 무의존 JDK 검증. 3단 토글+레지스트레이션 가드. **외부 의존성 0개**(logback-classic=Boot 기본 로깅·compileOnly+test). **사용자 환경 빌드 검증 완료(2026-06-03)** — 테스트 1건(`LogMaskingAutoConfigurationTest` phone-off 단언) 수정 후 22 통과. (실패는 모듈이 아닌 테스트 결함: `account=true` 의 계좌 정규식이 dashed 휴대폰을 잡아 발생 → 검증 입력을 dashless 로 교체. 계좌가 dash-grouped 숫자열을 잡는 건 의도된 동작이라 기본 off.)
- 🏁 **기본기능 갭 정리 종료(2026-06-03)**: 분산 락(lock)·PDF(pdf)·분산 캐시(cache-redis)·로그 마스킹(log-masking) 4종 완료. 이후는 갭이 아니라 심화/운영.
- ✅ **요청 컨텍스트 / 멀티테넌시(2026-06-03)**: **framework-context 신설** — 기본기능 카탈로그(`docs/BASELINE_FEATURES.md`) #5. 요청마다 `RequestContext`(불변·JDK단독, tenantId/userId/locale+확장 attributes)를 `ContextHolder`(정적 ThreadLocal, **상속형 아님** — 가상스레드/풀 누수 방지)에 바인딩(`ContextBindingFilter`, `MdcTraceFilter` 안쪽·+MDC), 종료 시 정리. 전파 **명시 2경로**: `ContextTaskDecorator`(@Async/풀에 컨텍스트+MDC) · `ContextPropagationInterceptor`(아웃바운드 헤더). 해소 전략 `ContextResolver` 교체 가능(기본 `HeaderContextResolver`, `@ConditionalOnMissingBean` → JWT/SecurityContext). 서블릿 한정. 3단 토글+레지스트레이션 가드. **외부 의존성 0개**(servlet/web=compileOnly+test). **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)**.
- ✅ **이미지 처리(2026-06-03)**: **framework-image 신설** — 기본기능 카탈로그 #7. `ImageProcessor` SPI(`process`/`thumbnail`/`probe`)+`DefaultImageProcessor`(JDK `javax.imageio`+`java.awt`). 비율유지 리사이즈/썸네일(상한 박스·업스케일 옵트인·2배 초과는 단계적 절반축소 고품질), **EXIF orientation 보정**(`ExifOrientation` 순수 JDK JPEG APP1/TIFF 파서 1~8, AffineTransform·5~8 가로세로 스왑), **민감 EXIF(GPS) 제거**(디코드→리인코딩 부수효과로 메타 미보존), 출력 포맷 화이트리스트(JPEG/PNG), **디컴프레션 폭탄 방지**(디코드 전 헤더 픽셀수 검사·기본 40MP), JPEG 알파 흰배경 평탄화, 헤드리스. 웹 비의존(배치 가능). 3단 토글 기본 off+레지스트레이션 가드. **신규 외부 의존성 0개**(web 불필요·엔진 전부 JDK). 엔진 javac 단독 + 기능 하니스 **26/26** 통과, config/배선은 context·pdf 패턴 미러(사용자 Gradle 검증 예정).
- 📋 **기본기능 카탈로그 신설(2026-06-03)**: `docs/BASELINE_FEATURES.md` — 기본기능 10항목 실측 체크(있음/부분/없음 + 위치 + 인수기준). #5 컨텍스트·#7 이미지 완료. 다음 활성=파일 하드닝 묶음(#8+#9+#10: 대용량 스트리밍/presigned·메타 정합성·AV 훅). 추가 요청은 §6 대기열로 수집.
- 🛠️ **환경정비+보안·검증+spotless(2026-06-03)**: 프로파일 **local/dev/prod 통일 + `local-xx` 오버레이**(local-postgres/redis/noauth), **감사 로그 DB 적재 활성화**(`audit_log` 마이그레이션 추가 — store.type=jdbc 의 3요건 중 누락분), **JWT 시크릿 prod 가드**(`JwtSecretSafetyGuard`), **요청 검증 빈틈 보강**(Spring7 `HandlerMethodValidationException`·로그인 `@Valid`), **spotless 다소스 확장**(Java=Palantir + gradle/yaml/sql/md, 설정 캐시 충돌 `lineEndings=UNIX` 해결). 문서 `docs/LOCAL_SETUP.md`·`CHANGES_AND_DEPRECATIONS.md`·`SECURITY_VALIDATION_ADDITIONS.md`·`SPOTLESS_NOTES.md`.
- ✅ **설정값(YAML) 패스워드 암호화 완료(2026-06-03)**: `framework-core` crypto 책임 확장 — 커스텀 Boot4 `EncryptedPropertyEnvironmentPostProcessor`(+`DecryptingPropertySource` enumerable 래퍼)가 `ENC(...)` 프로퍼티를 기존 `AesCryptoService`(AES-GCM, 마스터키 `framework.crypto.aes-secret`/`AES_SECRET`)로 지연 복호화. 등록은 `META-INF/spring.factories`(컨텍스트 이전). 토글 `framework.crypto.config-decryption.enabled`(기본 on). 토큰 생성 `CryptoCli`, prod 마스터키 가드 `AesMasterKeySafetyGuard`. **Jasypt 미도입**, 신규 의존성 0, Jackson 무관. 설계서 `docs/archive/NEXT_YAML_PASSWORD_ENCRYPTION.md`.
- 표기: ✅ 구현완료 · ⏭️ 다음 · (무표기) 예정. 세션 단위 상세는 `_internal/HANDOFF_SUMMARY.md`.

---

## 1. 표준 토글 규약 (모든 모듈 공통 — 이 3단을 반드시 따른다)

| 단계 | 무엇을 켜고 끄나 | 메커니즘 | 효과 |
|---|---|---|---|
| **1단 · 모듈** | 기능 묶음 자체의 존재 | `settings.gradle` include + 프로젝트 `build.gradle` 의존성 추가 → 모듈의 오토컨피그가 `@ConditionalOnClass(마커클래스)` | 의존성을 안 넣으면 클래스 자체가 없어 비용 0 |
| **2단 · 기능** | 모듈 안 개별 기능 | `framework.<module>.enabled` 및 세부 플래그 + `@ConditionalOnProperty` | yml 한 줄로 on/off |
| **3단 · 구현 교체** | 같은 인터페이스의 구현 선택 | `framework.<module>.<x>.type = memory \| redis \| jdbc …` (상호배제) + `@ConditionalOnMissingBean` | 환경별 백엔드 교체, 프로젝트가 빈 등록 시 자동 양보 |

**기본값 규칙**
- **코어 모듈**(core/security/mybatis): 모듈은 항상 탑재, 기능 플래그 기본 `true`. 단 모든 기능에 `enabled` 플래그를 둬 **끌 수 있게** 한다.
- **선택형 모듈**: 의존성을 넣어도 `framework.<module>.enabled` 기본 `false` → **명시적으로 켜야** 동작(안전 기본값).

**오토컨피그 스켈레톤(모든 신규 모듈 동일)**
```java
@AutoConfiguration
@ConditionalOnClass(Xxx.class)                                  // 1단: 모듈 존재
@ConditionalOnProperty(prefix = "framework.xxx", name = "enabled", havingValue = "true")  // 2단
@EnableConfigurationProperties(XxxProperties.class)
public class XxxAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                                   // 3단: 프로젝트 override 허용
    public XxxService xxxService(XxxProperties p) { ... }
}
```
등록: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 에 FQCN 한 줄.

---

## 2. 전체 모듈 카탈로그

> 빠른 그룹별 색인(폴더에서 바로 찾기)은 [`../framework/README.md`](../framework/README.md). 아래 §2 는 토글·분류·규제까지 포함한 설계 카탈로그.

분류 표기 — **[코어]** 항상 탑재(기능별 토글) · **[선택]** 의존성 추가형
규제 표기 — 공통(전부) · 공(공공) · 금(금융)

### 2.1 기존 (재사용)

| 모듈 | 책임 | 대표 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-core | 응답/에러/페이징/AOP/로깅/트레이스/XSS/캐시/AES + **설정값 암호화(`ENC(...)` 자동 복호화)** + **SI 공통 유틸(`util`: 검증·마스킹·날짜/영업일·금액·한글·해시·JSON + IO/스트림·CSV·고정폭전문·문자셋(CP949/EUC-KR)·텍스트(바이트절단)·컬렉션)** | `framework.core.{trace,httpLogging,xss,auditAspect,...}`, `framework.crypto.{enabled,aes-secret,config-decryption.enabled}` (util 은 토글 없는 정적) | [코어] | 공통 |
| framework-mybatis | 감사필드·암호화 타입핸들러·CurrentUser | (코어 연동) | [코어] | 공통 |
| framework-security | 인증추상화·JWT·TokenStore·RBAC·비번정책·로그인잠금 | `framework.security.*` | [코어] | 공통 |
| framework-openapi | API 문서 | `framework.openapi.enabled` | [선택] | 공통 |
| framework-redis | Redis TokenStore/LoginAttempt | `...type=redis` | [선택] | 공통 |
| framework-session | **서버 세션 모드의 Redis 세션 클러스터링**(Spring Session). 세션 모드 전환 자체는 framework-security 코어(`security.session.mode=session`); 이 모듈은 멀티 인스턴스에서 HttpSession 외부화(공유)만 담당 — framework-redis 의 "세션 버전" | `framework.security.session.mode=session` + 모듈 추가(`framework.session.enabled` 기본 on) | [선택] | 공통 |
| framework-commoncode | 공통코드 CRUD | `framework.commoncode.enabled` | [선택] | 공통 |
| framework-file / -s3 | 파일 저장(로컬/NAS/S3) + **콘텐츠 타입 검증(Tika, 옵트인)·확장자↔MIME 정합·at-rest 암호화(AES)·HTTP Range 스트리밍·S3 presigned PUT/GET·안티바이러스(ClamAV INSTREAM)** | `framework.file.enabled`,`storage.type`,`storage.encrypt`,`validation.content-type-detection`,`validation.enforce-extension-match`,`scan.enabled`,`scan.type` | [선택] | 공통 |
| framework-file-sftp | **SFTP(원격 SSH) 저장 백엔드** — `storage.type=sftp` 시 활성. Apache MINA SSHD 위임(순수 JDK SSH 불가 → BOM 밖 의존성, 모듈에만). `RangeReadableStorage`(부분 다운로드) 구현. 기본=연결마다 세션 개폐. **옵트인 연결 풀**(`BoundedObjectPool`: cap·LIFO 재사용·validate-on-borrow·maxIdle/maxLifetime)·**옵트인 키 회전**(`ReloadingSftpCredentialProvider`: 키 파일 변경 감지 재로드, 새 세션부터 적용). 호스트키 검증 기본 strict(known_hosts), 인증 password/private-key | `framework.file.storage.type=sftp` (+`sftp.{host,port,username,password,private-key-path,base-dir,strict-host-key-checking,pool.*,key-rotation.*,...}`) | [선택] | 공통 |

### 2.2 신규 — 토대 (다른 기능의 전제, 먼저 깔 것)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-i18n | MessageSource·에러메시지 외부화·다국어 | `framework.i18n.enabled` | [선택]→코어승격 권장 | 공통 |
| ✅ framework-idgen | 채번(Sequence/Table/Snowflake) | `framework.idgen.enabled` + `type` | [선택] | 공통 |
| ✅ framework-client | 외부 API 표준 클라이언트(타임아웃·재시도·서킷·연계로그) | `framework.client.enabled` | [선택] | 공통 |

### 2.3 신규 — 보안 완성 (ISMS-P·보안성 심의 대비, 공통 필수)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-security (확장) | 비번 **만료·변경주기·이력(직전 N개 재사용 금지)** | `framework.security.password.{expiry,history}.enabled` | [코어] | 공통 |
| ✅ framework-security (확장) | **동시(중복) 로그인 제어** | `framework.security.concurrent-session.enabled` | [코어] | 공통 |
| ✅ framework-audit | 접속/감사 로그 **DB 적재·조회** 표준(현 AOP 영속화) + **kafka 싱크**(messaging Outbox 발행) | `framework.audit.enabled` + `store.type=logging\|jdbc\|kafka` | [선택] | 공통 |
| ✅ framework-secure-web | 보안헤더·경로조작·인젝션 스크리닝·CSRF 더블서브밋(SQLi 등, XSS는 core) | `framework.secure-web.enabled` (+`headers`/`path-traversal`/`injection`/`csrf`) | [선택] | 공통 |
| ✅ framework-log-masking | **개인정보 로그 마스킹** — 자유 텍스트 로그 PII(주민/카드/휴대폰/이메일, 계좌 기본 off) 정규식 탐지 → core `MaskingUtils` 위임. (1) `SensitiveDataMasker` 빈 명시 호출 (2) Logback `%mmsg` 컨버터(정적 다리+폴백). 외부 의존성 0개(logback=compileOnly) | `framework.log-masking.enabled` (+`rules.*`/`custom-patterns`/`strip-newlines`/`install-converter`) | [선택] | 공통 |
| ✅ framework-context | **요청 컨텍스트 / 멀티테넌시** — 요청마다 `RequestContext`(tenantId/userId/locale+확장 attributes)를 `ContextHolder`(정적 ThreadLocal, 상속형 아님)에 바인딩/정리(+MDC). 전파 명시 2경로: `ContextTaskDecorator`(@Async/풀) · `ContextPropagationInterceptor`(아웃바운드 헤더). 해소 전략 `ContextResolver` 교체 가능(기본 헤더→JWT 대체). 서블릿 한정. 외부 의존성 0개(web=compileOnly) | `framework.context.enabled` (+`tenant-header`/`user-header`/`put-to-mdc`/`propagate-downstream`) | [선택] | 공통(횡단) |

### 2.4 신규 — 업무 생산성 (업무개발자 직접 사용)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-excel | POI 업/다운로드: **다운로드 SXSSF 스트리밍**(대용량 일정메모리)·**업로드 양식검증**(헤더/타입/필수/길이·패턴, 행별 오류수집). POI 타입 비노출(implementation) | `framework.excel.enabled` (+`export.window-size`/`import.max-rows`) | [선택] | 공통 |
| ✅ framework-batch | **Spring Batch 6 실행**(JobLaunchSupport: JobOperator 래핑+run.id 재실행보장)·**표준 로깅 리스너**·**Quartz cron 스케줄**(yaml 선언만으로 Job 기동) | `framework.batch.enabled`,`framework.scheduler.enabled` | [선택] | 공통 |
| ✅ framework-task | **Spring Cloud Task 5.0.1 실행이력**(Boot4 네이티브) — `@EnableFrameworkTask`(=`@EnableTask` 메타합성)로 한 번 실행 후 종료하는 작업(배치/마이그레이션/CronJob)의 시작·종료·종료코드·파라미터를 `TASK_EXECUTION` 에 영속. `FrameworkTaskExecutionListener` 표준 감사로깅. `framework-batch`(Batch6)와 결합 시 `TaskBatchAutoConfiguration` 이 Job↔Task 연결(`TASK_TASK_BATCH`). ⚠️ **SCDF 서버 본체는 OSS EOL(2025-04)** — Task 라이브러리만 생존, 메인 Oakwood BOM 밖이라 `spring-cloud-task-dependencies:5.0.1` 별도 BOM. 실투입 예제 `examples/batch-task-reference/` | `framework.task.enabled` | [선택] | 공통 |
| ✅ framework-notification | **메일/SMS/알림톡 채널 추상화** — NotificationService 가 ChannelType 라우팅. 메일=JavaMailSender, SMS·알림톡=벤더 SPI(SmsClient/AlimtalkClient)+기본 로깅구현(@ConditionalOnMissingBean 교체) | `framework.notification.enabled` + `channels.{mail,sms,alimtalk}.enabled` | [선택] | 공통 |
| ✅ framework-pdf | **PDF 산출물 생성**(거래내역서/통지서) — 표 기반 `PdfReport`/`PdfColumn` → `PdfExporter` OutputStream 스트리밍. **한글 TTF IDENTITY_H 임베딩**(미설정 시 라틴 폴백), 헤더 페이지반복+하단 페이지번호. 엔진 OpenPDF 2.0.2(iText4 LGPL/MPL, `com.lowagie` 패키지·implementation 비노출; 3.0+ `org.openpdf` 리네임이라 2.x 고정) | `framework.pdf.enabled` (+`page-size`/`landscape`/`margin`/`*-font-size`/`page-number`/`font.location`) | [선택] | 공통 |
| ✅ framework-image | **이미지 처리** — `ImageProcessor` SPI(`process`/`thumbnail`/`probe`)+`DefaultImageProcessor`(JDK ImageIO+AWT). 비율유지 리사이즈/썸네일(상한 박스·업스케일 옵트인·2배 초과 단계 축소)·**EXIF orientation 보정**(순수 JDK APP1/TIFF 파서 1~8)·**민감 EXIF(GPS) 제거**(리인코딩 부수효과)·출력 화이트리스트(JPEG/PNG)·**디컴프레션 폭탄 방지**(헤더 픽셀수 검사·기본 40MP)·JPEG 알파 평탄화·헤드리스. 웹 비의존(배치 가능). **외부 의존성 0개**(엔진 전부 JDK) | `framework.image.enabled` (+`default-format`/`thumbnail-max-edge`/`jpeg-quality`/`max-source-pixels`) | [선택] | 공통 |
| ✅ framework-archive | **아카이빙/압축** — `Archiver` SPI(`zip`/`unzip`/`unzipToDirectory`/`gzip`/`gunzip`)+`ZipArchiver`(순수 JDK `java.util.zip`). **스트리밍**(transferTo, 대용량 메모리 비적재)·엔트리 단위 콜백 해제·**zip-slip 차단**(`ArchiveSafety`)·**압축폭탄 가드**(엔트리수/엔트리크기/총바이트 상한). `ArchiveEntry`(지연 스트림)·`ArchiveErrorCode`(`ARC****`). **외부 의존성 0개**(tar/tar.gz 만 commons-compress 옵트인 후속) | `framework.archive.enabled` (+`max-entries`/`max-entry-size`/`max-total-bytes`) | [선택] | 공통 |
| ✅ framework-file-batch | **파일 일괄처리** — 여러 파일에 동일 작업(이름변경/이미지변환/압축)을 한꺼번에. `BatchFileOperation` SPI + `FileBatchProcessor`(부분실패 격리+결과수집·Java21 가상스레드+Semaphore 병렬·드라이런·입력순서 보존). 교차검증은 `BatchPreflight` capability(이름 충돌 검출). `RenameOperation`(prefix/suffix/regex/sequence/template, 충돌 FAIL/SUFFIX)·`ImageTransformOperation`(framework-image 위임)·`CompressOperation`(framework-archive 파일별 gzip 위임). image/archive 는 `compileOnly`+`@ConditionalOnClass`/`@ConditionalOnBean` 백오프(없으면 그 op 만 빠지고 rename·오케스트레이터는 동작). 순수 로직 Spring 무의존. **외부 의존성 0개.** 설계 `docs/archive/NEXT_FILE_BATCH_PROCESSING.md` | `framework.file-batch.enabled` (+`default-parallelism`) | [선택] | 공통 |
| ✅ framework-qr | **QR 코드 생성** — `QrGenerator` SPI + `ZxingQrGenerator`(ZXing `core` 인코딩만, **렌더링은 JDK ImageIO 직접** → `zxing-javase` 불필요). `BitMatrix`→`PixelGrid` 경계로 렌더러(`QrPngRenderer`)는 ZXing 무의존. PNG 전용·ECC L/M/Q/H·크기/마진/색/charset/최대길이 토글. mfa 의 otpauth:// URI 를 서버측 QR PNG 로 보완(mfa 는 의존성 회피로 미생성). **외부 의존성 1개**(`com.google.zxing:core`, BOM 밖→카탈로그+ext 고정, `implementation` 비노출) | `framework.qr.enabled` (+`default-size-px`/`default-margin`/`default-ecc-level`/`max-content-length`) | [선택] | 공통 |

### 2.5 신규 — 데이터/연계 (금융 핵심 ★)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ **framework-idempotency** | **정확히-한번/멱등키**(중복요청·중복결제 차단) + **응답 재생**(중복 시 저장 응답 재생) | `framework.idempotency.enabled` + `store.type=memory\|redis\|jdbc` (+`replay.enabled`) | [선택] | 금 ★ |
| ✅ framework-messaging | Kafka + **Outbox**(발행: 유실/중복 방지) **+ 소비자측 멱등 소비**(`IdempotentEventProcessor`: `x-event-id` 헤더로 중복 배달 1회 처리, 실패 시 키 해제→재배달 재처리). 멱등 저장소는 framework-idempotency(redis 권장) | `framework.messaging.enabled`(+`outbox.relay.enabled`)·`framework.messaging.consumer.enabled` | [선택] | 금 ★ |
| ✅ framework-datasource | **읽기/쓰기 분리 라우팅**(primary/replica) · **독립 다중 DB**(DB키별 SqlSessionFactory/tx매니저 세트, 앱이 `@MapperScan`/`@Transactional` 배선). 두 기능 상호 배타 | `framework.datasource.routing.enabled` / `framework.datasource.multi.enabled` | [선택] | 금/공 |
| ✅ framework-saga | **경량 오케스트레이션 Saga**(중앙 상태 + 실패 시 역순 보상). 단계 커맨드/리플라이는 **messaging Outbox 재사용**(상태변경과 한 트랜잭션=원자적), 상태 **JDBC 영속**(재기동 복구), **스턱 복구 폴러**(`SKIP LOCKED`, 옵트인). 전송·멱등 소비는 messaging, 본 모듈은 오케스트레이션만. 리플라이는 앱 `@KafkaListener`→`SagaReplyConsumer`. **참여자 멱등 키=`(saga-id, step)`** | `framework.saga.enabled`(+`recovery.enabled`) | [선택] | 금 ★ |

### 2.6 신규 — 규제 특화 (해당 사업만 켬)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-pki | GPKI/NPKI·전자서명·부인방지 | `framework.pki.enabled` | [선택] | 공/금 |
| ✅ framework-mfa | **2단계 인증(MFA)** — TOTP(RFC 6238)·OTP(SMS/메일/알림톡 `OtpSender` SPI)·**ISMS-P 일회용 복구코드**(SHA-256)·**WebAuthn/패스키 2차(독립 등록형, 2026-06-05)**. security 로그인이 `MfaGate` SPI 로 2단계 분기(미사용 시 단일단계 그대로). 챌린지 저장소 memory\|redis(멀티 인스턴스는 redis 필수)·등록 저장소 memory\|jdbc. WebAuthn factor 는 framework-webauthn 의 RP 연산·자격증명 저장소를 재사용(challenge 는 발급 티켓에 바인딩, SS 결합은 중첩 `@ConditionalOnClass` 로 격리). **외부 의존성 0개**(Base32/HOTP/TOTP/복구코드 전부 JDK; webauthn 은 spring-security-webauthn compileOnly) | `framework.mfa.enabled` + `policy=ENROLLED\|OFF`, `totp/otp/webauthn.enabled`, `challenge/enrollment.store.type` | [선택] | 금/공 |
| ✅ framework-oauth-client | **소셜 로그인(OAuth2) + OIDC RP 강화** — 외부 IdP 인가코드 흐름 → userinfo → `OAuthUserResolver`(앱 구현) 매핑 → security 로 **자체 JWT 발급**. google/kakao/naver 프리셋(중첩 응답 점 표기). state(CSRF) memory\|redis, 발급기 교체로 LoginService 통합. **OIDC**(per-provider `oidc.enabled`): id_token 검증(JWKS RSA/EC·HS · iss·aud·exp·nonce·sub)+discovery 자동적용+nonce 바인딩, jjwt 재사용. RestClient+Jackson3, **외부 의존성 0개** | `framework.oauth-client.enabled` + `providers.<id>.{client-id,client-secret}`, `state.store.type=memory\|redis`, `providers.<id>.oidc.{enabled,issuer,jwks-uri,discovery-uri,clock-skew,nonce}` | [선택] | 공통(B2C/SSO) |
| ✅ framework-saml-sp | **SAML 2.0 SP**(2026-06-04) — 외부 SAML IdP 연동(Spring Security SAML2 SP, IdP 메타데이터 등록) → `SamlUserResolver`(앱 구현) 매핑 → security 로 **자체 JWT 발급**(수기 JSON). 전용 SecurityFilterChain(`/saml2/**`)으로 framework-security 무수정. ⚠️ OpenSAML 전이=첫 "외부 의존성 0" 예외 → 루트 Shibboleth 저장소(그룹 한정) 필수. 멀티파드=redis AuthnRequest 저장소(`request-repository: redis`+HTTPS, 상관 쿠키 `SameSite=None;Secure`, 고정형 코덱; starter 부재 시 fail-fast) 또는 세션(기본). **IdP-initiated SLO 수신**(6.2-A): `slo.enabled=true`+`SamlLogoutUserResolver` 빈 → 중앙 로그아웃 시 우리 JWT 무효화(SAML 본체는 SS `saml2Logout` 위임). 사용 `docs/modules/SAML_SP.md` | `framework.saml-sp.enabled` + `registrations.<id>.metadata-uri`(+선택 `entity-id`/`email-attribute`/`name-attribute`) + `SamlUserResolver` 빈 (+SLO: `slo.enabled`+`SamlLogoutUserResolver`) | [선택] | 공공/대기업 SSO |
| ✅ framework-webauthn | **패스키/WebAuthn(FIDO2)**(2026-06-05) — 비밀번호 없는 강인증. **SS7 네이티브 `http.webAuthn()` DSL**(내부 WebAuthn4J) 래핑 → 패스키 ceremony(세션+CSRF 전용 SecurityFilterChain) 성공 시 세션 인증 수립 → 토큰교환 엔드포인트가 **프레임워크 표준 JWT 발급**(oauth/saml 패턴 대칭, `WebAuthnTokenIssuer`/`DirectWebAuthnTokenIssuer`). 자격증명 저장소 memory\\|jdbc(SS `Map*`/`Jdbc*Repository`, DDL 동봉). **+ 패스키 관리(2026-06-05): `GET/DELETE {credentials-path}` 목록·삭제, 삭제 소유권은 SS7 `CredentialRecordOwnerAuthorizationManager` 재사용(deny→404, 존재 비노출).** `@AutoConfiguration(after=SecurityAutoConfiguration)`+무가드 `@Order` 전용 체인으로 framework-security 무수정·메인 catch-all 체인 공존. **새 외부 의존성 0**(앱이 `spring-security-webauthn` 제공=compileOnly). ⚠️ 앱 `UserDetailsService` 빈 필수 + HTTPS(SecureContext) 전제. 사용 `framework/framework-webauthn/README.md` | `framework.webauthn.enabled` + `rp-id`/`rp-name`/`allowed-origins`/`token-path`/`credentials-path`/`store.type=memory\\|jdbc` + `UserDetailsService` 빈 | [선택] | 금/공(강인증) |
| framework-crypto-hsm | HSM 키관리(PKCS#11) | `framework.crypto.provider=hsm` | [선택] | 금 |
| framework-recon | 대사/정산 배치 | `framework.recon.enabled` | [선택] | 금 |
| framework-egov-compat | 전자정부 표준프레임워크 호환 어댑터 | `framework.egov.enabled` | [선택] | 공 |

### 2.7 신규 — 운영/관측

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-observability ✅ | 구조화(JSON) 로그·Micrometer 메트릭(공통 태그)·OTel 트레이스/메트릭 OTLP 익스포터 | `framework.observability.enabled` | [선택] | 공통 |
| ✅ framework-lock | **분산 락 / 스케줄러 리더 선출** — `DistributedLock` SPI(소유자 토큰 리스 기반 `tryLock/unlock/keepUntil` + 편의 `runIfLocked`), 백엔드 memory(단일JVM)\|redis(SET NX + Lua CAS)\|jdbc(INSERT 충돌·만료 재획득). **`@SchedulerLock`**(애스펙트)로 k8s 다중 파드 `@Scheduled` 중복 실행 방지(ShedLock 동등 `atMostFor`/`atLeastFor`, 네이티브). **외부 의존성 0개**(redis/jdbc=compileOnly, H2=test-scope) | `framework.lock.enabled` + `type=memory\\|redis\\|jdbc` (+`scheduler.enabled`) | [선택] | 공통 |
| ✅ framework-cache-redis | **분산 캐시(Redis 백엔드)** — core 로컬 Caffeine `CacheManager` 를 파드 간 공유 Redis 로 **대체**(`@AutoConfiguration(before=CacheAutoConfiguration)` 로 core 백오프). 값=JDK 직렬화(Jackson2 회피)·키=String, TTL/keyPrefix/null+캐시별 `ttls`, 앱 `RedisCacheConfiguration` 우선. 끄면 core Caffeine. **외부 의존성 0개**(spring-data-redis=compileOnly+test) | `framework.cache.redis.enabled` (+`time-to-live`/`key-prefix`/`cache-null-values`/`ttls`) | [선택] | 공통 |

> ✅ 구현완료(2026-06-03). **batch 모듈과의 차이**: Spring Batch 잡의 클러스터 중복방지는 Quartz `job-store-type=jdbc`. 본 모듈은 <b>평범한 `@Scheduled`</b>(Quartz 아님)의 중복방지 갭을 메우고, 임의의 단발 작업 단일 실행(`runIfLocked`)도 제공. 소유자 토큰으로 "내 락 만료 후 타 인스턴스 재획득분을 잘못 해제"를 차단(Redis=Lua CAS, JDBC=`WHERE lock_owner=?`). 운영(다중 replica)은 `type=redis\|jdbc` 필수(memory 는 파드 간 미배타). JDBC DDL `db/lock-postgres.sql`. 상세는 `framework/framework-lock/README.md`.

> ✅ 구현완료(2026-06-02). 공통 태그=`MeterRegistryCustomizer`(service/env/version+extra). 구조화 로그=Boot4 네이티브 `logging.structured.format`(ecs/logstash/gelf, 인코더 불필요). 익스포터=메트릭/트레이스 OTLP(기본 off, 호스트 runtimeOnly opt-in). 프로퍼티성 표준값은 `EnvironmentPostProcessor`(로깅 초기화 전)로 주입. **새 외부 의존성 0**. 상세는 `framework/framework-observability/README.md`, k8s 는 `deploy/k8s/observability.yaml`.

### 2.8 신규 — 테스트/아키텍처 검증 (테스트 전용, 배포 산출물 아님)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-archtest ✅ | **ArchUnit 아키텍처 규칙 강제** — 모듈(슬라이스) 순환금지 · Jackson3 규약(이동된 `com.fasterxml.jackson.*` 금지, `.annotation` 예외) · mapper/domain 레이어 격리 · `*AutoConfiguration`/`*Properties` 네이밍 · 필드주입 금지(생성자 주입). 전 모듈 main 을 `testImplementation project(...)` 로 임포트해 검사 | (토글 없음·`test` 자동) | [테스트] | 공통 |

> ✅ 구현완료·**BUILD 통과(2026-06-03)**. 7규칙, `@AnalyzeClasses(DoNotIncludeTests)`. **새 라이브러리 모듈 추가 시 `framework-archtest/build.gradle` 에 project 의존 한 줄을 추가**해야 검사 대상에 포함된다(누락 시 사각지대). 함께 도입: 핵심 알고리즘 단위테스트(JWT/TOTP/Base32/RBAC/마스킹) + 오토컨피그 로딩 테스트(`ApplicationContextRunner`) + **WireMock(standalone) 서비스간 연동 테스트**(`framework-client`: 재시도/서킷/POST 비재시도). archunit/wiremock 모두 **test 전용 → 런타임 무영향**.
>
> **오토컨피그 로딩 테스트 함정**: `ApplicationContextRunner` 가 설정 클래스를 리플렉션 introspect 하며 **모든 @Bean 파라미터/반환 타입을 로드**한다(@ConditionalOnClass 무관). 그 모듈의 `compileOnly` 의존을 **전부**(중첩 @Configuration·@Bean 반환 타입이 끌어오는 import 포함) `testImplementation` 으로 재선언해야 컨텍스트가 뜬다(예: mfa = web+jdbc+data-redis). 운영은 Boot 가 ASM 으로 읽어 무관.
>
> **전 라이브러리 모듈 스모크 완료(2026-06-03)**: 무테스트였던 14개(audit·batch·commoncode·excel·file·file-s3·i18n·idgen·messaging·mybatis·notification·openapi·redis·secure-web)에 로딩/토글 스모크 추가 → 무테스트 라이브러리 모듈 0. **서블릿 한정 오토컨피그**(`@ConditionalOnWebApplication(SERVLET)`: i18n 웹·secure-web)는 `WebApplicationContextRunner` 로. **`@MapperScan`+matchIfMissing(기본 ON)+MyBatis 결합**(commoncode·file)은 enabled 가 DataSource 를 요구 → 순수 스모크는 **disabled 백오프**까지(enabled 풀 와이어링은 DB 슬라이스). redis 는 오토컨피그 2개 중 `RedisTokenStoreAutoConfiguration` 만 `.imports` 등록됨(`RedisLoginAttemptAutoConfiguration` 미등록 = 다음 후보).
>
> **콘솔 한글 인코딩**: 테스트 워커/데몬은 `build.gradle`·`gradle.properties` 에서 UTF-8 고정. 콘솔 최종 렌더는 `gradlew` 클라이언트 JVM 이라 Windows 에서 깨지면 셸 `GRADLE_OPTS="...UTF-8"` 추가(데몬 변경 시 `--stop`).

---

## 3. 의존 관계 (요약)

```
core ──┬── mybatis ── (audit, datasource, commoncode, file)
       ├── security ──┬── redis(impl), audit(연동), mfa, oauth-client, pki
       │              └── idempotency(impl: redis/jdbc)
       ├── i18n            (모두가 메시지 사용)
       ├── idgen           (도메인 채번)
       ├── client ──── messaging/saga(연계)
       ├── observability   (전 모듈 횡단)
       ├── lock            (분산 락; impl: redis/jdbc, 횡단)
       ├── cache-redis     (분산 캐시; core Caffeine 대체, before=CacheAutoConfiguration)
       ├── log-masking     (로그 PII 마스킹; core MaskingUtils 위임, Logback %mmsg)
       ├── pdf             (PDF 산출물; OpenPDF impl 비노출, 한글 TTF 임베딩)
       ├── context         (요청 컨텍스트/멀티테넌시; ThreadLocal 비상속, @Async·아웃바운드 명시 전파)
       └── image           (이미지 처리; ImageIO+AWT, 웹 비의존, 외부 의존성 0)
```
원칙: 상위(토대)는 하위를 모른다. 순환 금지. impl 모듈(redis 등)이 추상(security/idempotency)을 의존. **saga 는 messaging(Outbox) 위에 오케스트레이션만 얹는다**(전송/멱등 소비 재사용, compileOnly 비전이라 의존 서비스가 messaging 도 명시).

---

## 4. 구축 순서 (금융 우선, 토대→심의→생산성→연계→규제→운영)

1. **토대** — framework-i18n, framework-idgen, framework-client
2. **보안 완성(심의)** — 비번 만료/이력, 동시로그인, framework-audit, framework-secure-web
3. **금융 핵심** — **framework-idempotency**, framework-messaging(+Outbox), framework-datasource
4. **업무 생산성** — framework-excel, framework-batch, framework-task, framework-notification, framework-pdf, framework-image
5. **규제 특화** — framework-pki, framework-mfa, framework-crypto-hsm, framework-recon, (공공 시) framework-egov-compat
6. **운영/관측** — framework-observability ✅ · framework-lock ✅(분산 락·`@Scheduled` 중복방지) · framework-cache-redis ✅(분산 캐시) · framework-log-masking ✅(개인정보 로그 마스킹) · framework-context ✅(요청 컨텍스트/멀티테넌시·횡단)
7. **그릇 정비** — 게이트웨이(폴백·CORS·rate-limit)·k8s(redis/secret/멀티서비스)·CI/CD 멀티서비스화

> 1·2단계 산출물은 3단계 이후 모든 모듈이 재사용한다(메시지·채번·연계·감사). 토대를 건너뛰면 각 모듈이 재발명한다.

---

## 5. 프로파일 프리셋 (사업유형별 일괄 on/off)

같은 코어 + yml 프리셋으로 사업유형을 전환한다.

```yaml
# application-finance.yml  (금융 풀세트)
framework:
  idempotency: { enabled: true,  store: { type: jdbc }, replay: { enabled: true } }  # 중복 차단+응답 재생, 영속 공유
  messaging:   { enabled: true, outbox: { relay: { enabled: true } } }
  datasource:  { routing: { enabled: true } }   # primary/replica 읽기·쓰기 분리 (또는 multi.enabled=true 로 독립 다중 DB — 둘은 배타)
  audit:       { enabled: true,  store: { type: jdbc } }
  mfa:         { enabled: true }
  pki:         { enabled: true }
  security:    { password: { expiry: { enabled: true }, history: { enabled: true } },
                 concurrent-session: { enabled: true } }
```
```yaml
# application-public.yml  (공공)
framework:
  egov:  { enabled: true }
  pki:   { enabled: true }
  audit: { enabled: true, store: { type: jdbc } }
  secure-web: { enabled: true }
```
```yaml
# application-enterprise.yml  (일반 기업 라이트 — 규제 특화 off)
framework:
  idempotency: { enabled: true, store: { type: redis } }   # 중복요청 방지는 보편적
  audit:       { enabled: true, store: { type: jdbc } }
  # egov/pki/mfa/hsm/recon/saga: 미설정 → 기본 false → 꺼짐
```

레퍼런스 구현은 `framework/framework-idempotency/` (전 3단 토글 적용 예) 참고.
