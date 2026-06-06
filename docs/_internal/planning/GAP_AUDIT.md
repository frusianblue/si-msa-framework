# GAP_AUDIT.md — 전 프레임워크 소스 미완료·보충 감사 (2026-06-05, 재감사 2026-06-06)

> 방법: 라이브 레포(master) 전체를 정적 스캔 — TODO/FIXME 마커, 미구현 스텁(`UnsupportedOperationException`/placeholder), `@Deprecated`/deprecation 억제, `.imports` 등록 누락, 무테스트 모듈, SPI 백엔드 대칭성, 참조 DDL 실재, 문서상 보류 항목과 코드 대조, devops 매니페스트 인벤토리.

---

## ⭐ 2026-06-06 재감사 (master `241e350` 코드 실측 — 이 블록이 현재 정본)

> 직전(06-05) 감사 이후 **A1(WebAuthn)이 완료**됐다. A4·A9 는 06-05 시점에 이미 완료(아래 본문 ✅). master 코드를 항목별 실측한 결과:

| # | 항목 | master 실측 | 근거(코드) |
|---|---|---|---|
| A1 | WebAuthn/Passkey | ✅ **완료** | `framework/framework-webauthn`(11 java, 신규모듈 체크리스트 4종 충족, `FRAMEWORK_MODULES` ✅) |
| A4 | ConcurrentSession Redis | ✅ 완료 | `RedisConcurrentSessionService`(Lua 원자 register)+`RedisConcurrentSessionAutoConfiguration` |
| A9 | 게이트웨이 aud 검증 | ✅ 완료 | `GatewayAuthProperties.audiences`(옵트인)+`GatewayJwksTokenVerifier` |
| A2 | SAML SP-initiated SLO | ⬜ **열림** | IdP-initiated(`SamlSloService`/`SamlSloLogoutHandler`)만 — SP-initiated(`Saml2LogoutRequest` 생성/`/saml2/logout` 진입) 부재 |
| A3 | 서명키 KMS/Vault | ⬜ **열림** | `AesSigningKeyCipher`만(`SigningKeyCipher` SPI) — KMS/Vault 구현체 부재 |
| A5 | archive tar/tar.gz | ⬜ 열림 | `ZipArchiver`만(`Archiver` SPI, `ArchiveSafety`) |
| A6 | S3 멀티파트(TransferManager) | ⬜ 열림 | `S3FileStorage` 단순 putObject — TransferManager/멀티파트 부재 |
| A7 | RetryUtils(core) | ⬜ 열림 | `framework-core` 범용 재시도 헬퍼 부재 |
| A8 | 규제특화 pki/hsm/recon/egov | ⬜ 열림 | 해당 모듈 0(수요 기반 백로그) |
| B | LoginAttempt jdbc 백엔드 | ⬜ 열림(낮음) | InMemory+Redis만(휘발성이라 jdbc 필요성 낮음). ※ "B: ConcurrentSession=Redis 없음"은 A4 완료로 **해소** |

**현재 남은 실제 보충 = A2 · A3 · A5 · A6 · A7 · A8 · B (7개).** 미구현 스텁/무테스트 모듈/`.imports` 누락은 여전히 0.

### k8s 실배포 검증 범위 (정직화 — "kind ✅"가 가리는 부분)
kind 실배포로 검증된 것은 **`overlays/local` 6파드 `1/1 Running` + DbAuthenticator authorization_code+PKCE 토큰 플로우**뿐. 아래는 **클러스터 미실증**:
- **overlays `dev`/`prod`** — 렌더링/`--dry-run=server`까지만, 실 apply 미검증.
- **애드온** — metrics-server(HPA)·kube-prometheus-stack(ServiceMonitor)·ingress-nginx 는 전부 외부 Helm 전제 **선택** + local overlay 는 ServiceMonitor 제거 → kind 실증 0.
- **상위 인증 흐름** — OIDC RP 풀콜백·SAML SP·SLO·게이트웨이 이중 발급기·webauthn 은 `@SpringBootTest`/JDK e2e 레벨이지 kind 실배포 위 검증 아님.
- **운영 부적합 잔재** — 이미지 태그 `:local`+`IfNotPresent`(불변 태그/실 레지스트리 아님) · 볼륨 emptyDir/`/tmp`(PVC 아님).

→ **다음 단계 = 실배포 검증 + 애드온**: 착수 설계 `NEXT_K8S_REAL_DEPLOY.md`(volume/PVC · 불변 이미지 태그+레지스트리 · 애드온 클러스터 실증 · dev/prod overlay 실 apply).

---

## (이하 2026-06-05 원본 감사 — 이력 보존)

## 한 줄 결론
**구현 본체는 매우 완결적이다.** 미구현 스텁 0 · 무테스트 라이브러리 모듈 0 · `.imports` 누락 0(과거 redis `RedisLoginAttemptAutoConfiguration` 누락은 **이미 수정됨**) · 코드가 참조하는 DDL 전부 존재 · 실제 `TODO` 마커는 1건(SAML SS8 마이그레이션). 남은 것은 **명시적으로 보류된 기능 + 일부 SPI 백엔드 비대칭 + README 양식 비일관 + k8s devops 산출물 부재**다.

## 처리 현황 (2026-06-05 후속 — 추천 우선순위대로)
- ✅ **C 완료** — 전 36개 모듈 README 에 `끄는 법` 섹션 통일(없던 10개 신설, 실제 토글·기본 off·폴백 근거). 켜기·실전·끄기 36/36 일관 + 코드펜스 짝수.
- ✅ **D 완료(매니페스트)** — `base/common/{ingress,networkpolicy,pdb}.yaml` 신설 + base kustomization 등록 + prod overlay TLS/실도메인 Ingress 패치. `K8S_ADDONS.md` 갱신(요구 매핑·ingress 노트). 단 **레이트리밋 429 Testcontainers(Redis) 통합테스트는 미작성**(Docker+Maven 필요 → 항목 E, 받는 쪽 환경에서).
- ✅ **A4 완료** — `framework-redis` 에 `RedisConcurrentSessionService`(register 를 Lua 로 원자 실행) + `RedisConcurrentSessionAutoConfiguration` + `.imports` 등록 + 토글/등록 가드 테스트. `store.type=memory|jdbc|redis`. (실 Redis Lua 라운드트립은 받는 쪽 검증.)
- ✅ **A9 완료** — 게이트웨이 `GatewayJwksTokenVerifier` 에 옵트인 `aud` 검증 추가(`gateway.auth.authorization-server.audiences`) + 통과/거부 테스트. 비우면 하위호환.
- ✅ **A1 완료(2026-06-06)** — `framework-webauthn`(SS7 네이티브 `http.webAuthn()` 래핑). 남은 독립 세션 권장: A2(SP-initiated SLO)·A3(KMS/Vault). A5~A8 선택 백로그.

## 오해 방지 — 핸드오프엔 "다음"이었으나 이미 완료된 것
- **RP id_token 링크 e2e** — `services/auth-server/src/test/java/.../e2e/OidcRpLinkageTest.java`(279줄, 5+테스트: 정상검증·JWKS 캐시 재사용·잘못된 issuer/aud 거부) **완료**.
- **saga step-timeout** — `SagaProperties.stepTimeout`(기본 60s) + `JdbcSagaStore` `deadline_at` + 복구 폴러(`findStuck`) **구현됨**.
- **서명키 회전** — auth-server `SigningKeyCipher`(AES) + 회전 스케줄러 존재.

---

## A. 실제 미구현 기능 (코드 부재, 대부분 의도적 보류)

| # | 항목 | 현 상태(근거) | 권장 |
|---|---|---|---|
| A1 | **WebAuthn / Passkey (Passwordless)** | ✅ **완료(2026-06-06)** — `framework-webauthn`(11 java, SS7 네이티브 `http.webAuthn()` 래핑) | — (킥오프 스펙 `NEXT_WEBAUTHN.md` 는 archive 대상) |
| A2 | **SAML SP-initiated SLO (6.2-B)** | IdP-initiated(6.2-A)만 구현. `SamlSloService` 는 무상태 수신 위주 | SS `saml2Logout` SP-initiated 경로 + 세션 결합 |
| A3 | **서명키 KMS/Vault 백엔드** | `SigningKeyCipher` SPI + **AES 구현만**. 주석 "KMS/Vault 는 이 빈만 교체"(결정 ①: AES 시작·KMS 후속) | prod 키관리 요구 시 KMS/Vault 구현체 |
| A4 | ~~ConcurrentSessionService Redis 백엔드~~ | ✅ **완료** — `RedisConcurrentSessionService`(Lua 원자 register) + 오토컨피그 + 테스트. `store.type=memory\|jdbc\|redis` | — |
| A5 | **archive tar / tar.gz** | `Archiver` = zip + gzip 만(`ZipArchiver`) | commons-compress 로 tar/tar.gz(옵트인) |
| A6 | **S3 멀티파트 병렬 업로드(TransferManager)** | `S3FileStorage` 단순 putObject | 대용량 업로드 시 TransferManager |
| A7 | **RetryUtils (core util)** | 없음(client 모듈에 호출단 재시도는 있음) | 범용 재시도 헬퍼(선택) |
| A8 | **규제특화 pki/hsm/recon/egov** | 코드 0(백로그) | 해당 사업 수주 시 |
| A9 | ~~게이트웨이 AS `aud` 클레임 검증~~ | ✅ **완료** — `GatewayJwksTokenVerifier` 옵트인 `aud` 검증(`...authorization-server.audiences`) + 테스트. 비우면 하위호환 | — |

## B. SPI 백엔드 비대칭 (보충 후보 — 우선순위 낮음, 대부분 설계상 OK)
| SPI | 보유 | 비고 |
|---|---|---|
| ConcurrentSessionService | InMemory·Jdbc | **Redis 없음** → A4 |
| LoginAttemptService | InMemory·Redis | **Jdbc 없음**(실패카운트는 휘발성이라 redis 적합 — 보충 필요성 낮음) |
| SagaStore | Jdbc | 영속 필수라 jdbc 단일은 합리적 |
| MfaChallengeStore | InMemory·Redis | 휘발성 챌린지 — OK |
| MfaEnrollmentStore | InMemory·Jdbc | 영속 등록 — OK |
| TokenStore / IdempotencyStore / DistributedLock | InMemory·Jdbc·Redis | 완전 |

## C. 문서 / 일관성 보충
- **모듈 README 양식 비일관** — 표준 `끄는 법` 섹션 없는 모듈 8개(cache-redis·context·file-sftp·image·log-masking·observability·qr) + `켜기/끄기` 헤더 자체가 다른 3개(datasource·saga·secure-web). 이번 세션의 `실전 사용 예` 는 36/36 통일됐으나 **끄는 법/덮어쓰기 헤더 통일은 미완**.
- (참고) 코드 참조 DDL·`.imports` 는 모두 정상.

## D. devops / k8s 산출물 부재 (`deploy/k8s`)
| 산출물 | 현황 |
|---|---|
| **Ingress (+ ingress-nginx)** | ❌ 0 (게이트웨이 외부 노출 매니페스트 없음) |
| **NetworkPolicy** | ❌ 0 (백엔드 인그레스 게이트웨이 한정 제한 없음) |
| **PodDisruptionBudget** | ❌ 0 (롤링/드레인 가용성 보장 없음) |
| HorizontalPodAutoscaler | ✅ 1 (`overlays/prod/hpa.yaml`) |
| ServiceMonitor / deployment-hardening | ✅ 존재 |
| **레이트리밋 429 Testcontainers(Redis) 통합테스트** | ❌ 없음 |
| prod Redis 매니지드 전환 | 현재 `base/redis/redis.yaml` 자체 호스팅(예시) |

## E. 검증 보류 (코드는 있으나 작성환경 Maven Central 차단으로 미실행 → 받는 쪽 로컬 확인 필요)
- 게이트웨이 런타임 점검(WebFlux 실기동), SAML 본체 라운드트립(OpenSAML), auth-server 실기동 — 문서에 "받는 쪽 검증" 표식(`modules/SAML_SP.md`·`GATEWAY_EDGE_AUTH.md`·`AUTH_SERVER.md`).
- 전반: 단위/오토컨피그 테스트는 작성됨, 통합/실기동은 로컬 의존.

---

## 추천 처리 순서
1. ~~C(README 끄는법/덮어쓰기 헤더 통일)~~ ✅ 완료
2. ~~D(Ingress·NetworkPolicy·PDB)~~ ✅ 완료(레이트리밋 Testcontainers 테스트만 E 로 잔여)
3. ~~A4(ConcurrentSession Redis)·A9(게이트웨이 aud)~~ ✅ 완료
4. ~~A1(WebAuthn)~~ ✅ 완료(2026-06-06)
5. A2(SP-initiated SLO)·A3(KMS/Vault) — 각각 독립 세션 권장(규모 큼)
6. A5~A8(tar·S3 멀티파트·RetryUtils·규제특화) — 선택 백로그

## 남은 보충 항목 (요약 — 2026-06-05 현재)
| 우선 | 항목 | 규모 | 비고 |
|---|---|---|---|
| ~~▶ 다음~~ ✅ 완료 | ~~A1 WebAuthn/Passkey~~ (2026-06-06) | 모듈 1개 | `framework-webauthn`. 킥오프 스펙 archive 대상 |
| 후속 | A2 SAML SP-initiated SLO | 중 | IdP-initiated 는 구현됨 |
| 후속 | A3 서명키 KMS/Vault 백엔드 | 중 | SPI(`SigningKeyCipher`)+AES 만 존재 |
| 잔여 | E 레이트리밋 429 Testcontainers(Redis) 통합테스트 | 소 | Docker+Maven 필요(작성환경 불가) |
| 백로그 | A5 tar/tar.gz · A6 S3 멀티파트 · A7 RetryUtils · A8 pki/hsm/recon/egov | 소~중 | 수요 기반 |
| 낮음 | B: LoginAttemptService jdbc 백엔드(휘발성이라 필요성 낮음) | 소 | — |

> 구현 본체에서 **새로 발견된 미완은 없다**(이번 감사 기준). 위 항목은 전부 "처음부터 의도적으로 보류"였던 후보 기능 + 검증환경 제약 1건.
