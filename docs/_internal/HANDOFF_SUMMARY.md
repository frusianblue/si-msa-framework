# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**문서 전면 정비 — 역할별 재구조화 + 사용/오너 가이드 + 함정 대장(2026-06-05).** 흩어진 문서(루트/ docs 혼재)를 **역할별 폴더**로 재배치하고(`docs/00_INDEX.md` 진입점 + `guide/`·`reference/`·`ops/`·`_internal/`), **전 35개 모듈 README**(없던 19개 신설)·**개발자 가이드/샘플**·**오너용 인증 구성 결정 가이드(레시피 R1~R6)**·**JWT·무상태 함정 사례집**·**전 영역 함정 대장(append-only)**을 신설했다. 코드 변경 0(문서·README·스크립트만). 핵심 = 더 이상 `_internal/HANDOFF`(196KB·세션로그)에 묻히지 않게, 개발자/오너/운영이 `00_INDEX` 한 장에서 출발하고 함정은 `guide/PITFALLS.md` 에 **계속 누적**한다. 받는 쪽 = 각 zip 을 레포 루트에서 `unzip -o` (1단계는 `reorg-docs.sh` 1회 실행).

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: 문서 전면 정비 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) — **스택 무변경(문서 작업)**

## 직전에 한 것 (Done — 문서, 전 레포 마크다운 링크 0 broken 검증)
- **1) 역할별 재구조화** — `docs/00_INDEX.md`(역할별 front door) 신설 + `reorg-docs.sh`(git mv 이동 + 깨진 링크 자동 보정). 작업/세션기록(HANDOFF·APPLY·NEXT·SPOTLESS)→`docs/_internal/`, 레퍼런스→`docs/reference/`, 운영→`docs/ops/`, 기능문서→`docs/modules/` 유지, `docs/FRAMEWORK_MODULES.md` 유지. **README.md 1003줄→슬림화**(무엇·5분 퀵스타트·`00_INDEX` 이정표).
- **2) 모듈 README 19개 신설 → 전 35개 모듈 README 보유**(core·mybatis·security 등 무문서 해소). 양식 통일: 켜는 법/쓰는 법/끄는 법/덮어쓰기/버전 관리. 각 모듈 실제 소스(오토컨피그·토글 prefix·SPI·메서드 시그니처) 근거.
- **3) `docs/guide/DEVELOPER_GUIDE.md`** — 표준 응답/예외/페이징/인증/MyBatis/util 16종/로깅/암호화/테스트/하지말것(ArchUnit 차단) 11절. (§5 매퍼스캔: 앱은 plain `@MapperScan`, SPI 혼재 모듈만 annotationClass — 실코드 확인 후 정정.)
- **4) `docs/guide/SAMPLES.md`** — 새 서비스 골격(의존성/yml/Flyway) + 완결 CRUD 한 슬라이스(user-service 실패턴) + 모듈 샘플(file/excel/idempotency/client/commoncode). "이 문서만으로 첫 기능까지".
- **5) `docs/guide/AUTH_COMPOSITION_GUIDE.md`(오너용)** — 인증 방식/상태관리/소셜/MFA/엔터프라이즈를 **결정 카테고리별 옵션 + 구현상태(✅/🟡/⬜) + 표준 레시피 R1~R6**. ⬜ 명시(Passkey/WebAuthn·서버세션·Keycloak 전용모듈) + 추가 절차.
- **6) `docs/guide/JWT_STATELESS_PITFALLS.md`** — 과거 대화 검색으로 복원한 JWT·무상태·principal 실사례 9건(증상→원인→해결→교훈) + 확장 가이드(폐기 강화·커스텀 provider 체크리스트·세션전제 무상태화·회귀방지). 대표 = OIDC `auth_time` ← `FactorGrantedAuthority`(SessionInformation 오진 정정).
- **7) `docs/guide/PITFALLS.md`(함정·교훈 대장, append-only)** — `_internal/HANDOFF §6` 의 60+건을 8개 카테고리로 큐레이팅([일반]/[겪음] 태그·★재발·빠른 자가진단표). **새 함정은 여기 계속 쌓는 규칙** + Claude 기억에 standing instruction 등록.
- (부수) `GETTING_STARTED.md`·`USAGE_BY_PROJECT_TYPE.md` 도 실내용으로 채움. `00_INDEX` 오너/주제 색인에 신규 가이드 전부 연결.

## 새로 확정한 함정/규칙 (PITFALLS.md 로 이관·HANDOFF §6 유지)
- **함정은 `docs/guide/PITFALLS.md` 에 누적한다(신규 규칙)** — 빌드/런타임 에러·gotcha 를 만나면 즉시 한 줄(분류 [일반]/[겪음] + 증상→원인→해결). `_internal/HANDOFF §6` 는 원문 세션로그, `PITFALLS.md` 는 큐레이팅 자산.
- **문서는 역할로 가른다** — 작업/세션 기록은 `_internal/`(개발자 비노출), 사용/결정 가이드는 `guide/`, 진입은 `00_INDEX`.
- **재배치 후 상대 링크 보정 필수** — 깊이 바뀐 문서(reference/ops 이동)·서비스 README 의 `../../docs/<file>` 경로를 새 위치로(스크립트가 자동 처리, 전 레포 0 broken 검증).

## 실행/검증 (받는 쪽)
```bash
# 적용(레포 루트). 1단계는 스크립트 1회 실행, 나머지는 unzip 만.
unzip -o docs-reorg.zip && bash reorg-docs.sh   # ① 재구조화
unzip -o module-readmes.zip                      # ② 모듈 README 19
unzip -o samples-and-guide.zip                   # ③④ 개발자가이드+샘플
unzip -o auth-composition-guide.zip              # ⑤ 인증 구성 결정 가이드
unzip -o jwt-stateless-pitfalls.zip              # ⑥ JWT 함정+확장
unzip -o pitfalls-ledger.zip                     # ⑦ 함정 대장
# 확인
find docs -maxdepth 2 -type f | sort
# (코드 무변경이라 gradle 영향 없음. 링크 검증은 작성환경에서 전 레포 0 broken 완료.)
```

## 다음 (Next) 후보
- (문서) 결정 가이드 패턴을 다른 축으로 확장 — **데이터 정합성**(멱등/Outbox/Saga 조합)·**파일 저장**(local/S3/SFTP)·**관측**(로그/메트릭/트레이스). 형식은 `AUTH_COMPOSITION_GUIDE` 미러.
- (코드, 그릇 정비 종료 후 잔여) **devops 백로그** — gateway Ingress 리소스+ingress-nginx · NetworkPolicy(백엔드 인그레스 제한) · prod redis 매니지드 · PodDisruptionBudget · 레이트리밋 429 Testcontainers Redis 통합테스트.
- (코드, 보류) OIDC **B안** 전체 흐름 e2e(confidential demo-rp) · 게이트웨이 AS aud 검증 · 서명키 **KMS/Vault** 백엔드 · SAML **6.2-B** SP-initiated SLO · **6.4** Passwordless(**WebAuthn** — ⬜ 미구현, AUTH_COMPOSITION_GUIDE §7) · user-service Testcontainers 통합테스트.
- (선택 백로그) 아카이빙 tar/tar.gz(commons-compress) · RetryUtils · 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · S3 멀티파트 병렬 업로드(TransferManager).
<!-- 갱신 끝 -->
