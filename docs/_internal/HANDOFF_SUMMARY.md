# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ kind "OAuth2 클라이언트 등록 → 토큰 플로우"(=DbAuthenticator 운영 인증 경로) 완료(2026-06-06).** prod 클라이언트 0건(설계: LocalDemo `@Profile("local")`)을 깨지 않는 **옵트인 시더** `SmokeClientSeeder`(`@ConditionalOnProperty framework.auth.seed-smoke-client`, 기본 false)로 `demo-web`(public+PKCE)·`demo-service`(client_credentials)를 `repo.save()` 등록. 자동 테스트 `SmokeClientDbAuthFlowTest`(`@ActiveProfiles("smoketest")` = `!local`→DbAuthenticator+prod 하드닝 회피) 통과(sub=`tester`) **+ kind 실배포에서 `oauth2_registered_client` 2 rows 등록 확인**. 배포 중 드러난 **빌드/배포 함정 2건(코드 아님, 이미지 전달 문제)**: ① `.dockerignore` 부재 → 호스트 `build/`·`.gradle/` 누수로 stale jar(`.dockerignore` 신규로 영구 해소) ② 같은 `:local` 태그 + `IfNotPresent` → 노드 containerd 옛 digest 재사용(유니크 태그 `:local-N` 우회). 스펙 `NEXT_KIND_AUTH_TOKEN_FLOW.md` ✅ARCHIVED. **다음 섹션 = confidential `demo-rp` 전체 콜백 흐름**(`NEXT_RP_IDTOKEN_LINK §B`).

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: smoke 시더 + DbAuthenticator 토큰 플로우 완료 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — 스택 무변경.

## 직전에 한 것 (Done)
**A안(prod-안전 smoke 시더) 구현 → 자동 테스트 + kind 실배포 검증 완료.** 변경 파일:
| 파일 | 내용 |
|---|---|
| `services/auth-server/.../config/SmokeClientSeeder.java` (신규) | 옵트인 시더. `@ConditionalOnProperty(framework.auth.seed-smoke-client, havingValue=true)`(기본 off) + `ApplicationRunner` 가 `findByClientId==null` 일 때만 `repo.save()`(멱등). demo-web=public+PKCE+AC+RT+openid/profile, demo-service=client_secret_basic+client_credentials+api.read. |
| `services/auth-server/.../resources/application.yml` | `framework.auth.seed-smoke-client: ${FRAMEWORK_AUTH_SEED_SMOKE_CLIENT:false}` 노출. |
| `services/auth-server/src/test/resources/application-smoketest.yml` (신규) | `!local`+플래그 on+H2(`authdb_smoke`) — prod 하드닝 회피하며 DbAuthenticator 활성. |
| `services/auth-server/src/test/.../e2e/SmokeClientDbAuthFlowTest.java` (신규) | 시더 등록 단언 + `tester` DbAuthenticator authorization_code+PKCE → access/id_token(sub=tester) + demo-service client_credentials. **받는 쪽 통과 확인됨.** |
| `deploy/k8s/overlays/local/kustomization.yaml` | `auth-server-config` ConfigMap 패치로 `FRAMEWORK_AUTH_SEED_SMOKE_CLIENT: "true"`. |
| `.dockerignore` (신규) | `COPY . .` 가 호스트 `build/`·`.gradle/` 를 안 담도록 — 이미지 빌드 hermetic 화(stale jar 영구 차단). |
| 문서 | `AUTH_SERVER.md` §6.5(절차 + apply-k/노드캐시 주의)·§8(검증 완료) · `PITFALLS §1`(.dockerignore)·`§9`(노드 캐시 함정·시더 옵트인) · 스펙 ✅ARCHIVED. |

## 현재 상태 (적용/검증)
- **✅ kind 실배포 = OAuth2 클라이언트 등록 + DbAuthenticator 토큰 플로우 검증 완료**: `oauth2_registered_client` 2 rows(demo-web/demo-service), 자동 테스트 통과.
- **✅ kind 첫 배포 6파드 그린**(이전 세션) · **✅ compose 그린**(회귀용).
- 현재 인프라: Docker Desktop kind, si-msa ns. auth-server 는 시더 포함 이미지(유니크 태그로 적재)로 기동 중.
- 작성환경 Maven Central·릴리스 CDN 차단 → 빌드/kubectl 직접 실행 불가(정적 작성 + 받는 쪽 실행); 이번 검증은 받는 쪽(Chae) 실기동으로 완료.

## 바로 다음 할 일 (Next)
1. **commit/push** — 이번 변경(시더+.dockerignore+테스트+문서) + 이전 게이트웨이 런타임 점검 누적분.
2. **다음 섹션 = confidential `demo-rp` 전체 콜백 흐름**(`NEXT_RP_IDTOKEN_LINK §B`): RP `OAuthClient.exchangeCodeForTokens`(`client_secret_post`). 이번 smoke 시더를 출발점으로 `demo-rp`(confidential) 등록 추가.
- 백로그: 모듈 README 샘플 코드 롤아웃, CI 게이트 + Jacoco aggregate, SP-initiated SLO, WebAuthn, K8s addons(metrics-server/Prometheus/ingress).

## 이번 세션에서 새로 박힌 함정/원칙 (되돌리지 말 것 — 전부 PITFALLS)
- **`.dockerignore` 부재 = 이미지 빌드 비결정 결함**(§1): `COPY . .` 가 호스트 `build/`·`.gradle/` 를 담아 컨테이너 Gradle 이 옛 산출물 up-to-date → stale jar. `--no-cache` 로도 안 고쳐짐(레이어 캐시만 무효화). → `.dockerignore` 로 hermetic. **컨테이너 이미지 빌드는 호스트 빌드 상태에 의존 금지 — 항상 소스 클린 빌드.**
- **같은 `:local` 태그 재빌드 = 노드 캐시 함정**(§9): containerd 가 옛 digest 를 `IfNotPresent` 로 재사용 → `delete pod` 로도 안 바뀜. 진단=파드 imageID vs `docker images` digest 비교, 해결=유니크 태그(`:local-N`). 로컬 반복 빌드는 고정 태그보다 유니크 태그가 안전.
- **이미지 검증은 `docker run --entrypoint sh <img> -c 'grep ...'`**(ENTRYPOINT 가 `java -jar` 라 `docker run <img> sh -c` 는 앱이 떠버림). `kubectl exec -- sh -c` 는 무관.
- **ConfigMap 플래그는 `apply -k` 필요**: `rollout restart` 만으론 기존 ConfigMap 으로 떠서 새 env 안 들어감.
- **prod 클라이언트 0건은 설계 — 옵트인 시더로 우회**(`framework.auth.seed-smoke-client`, 프로파일/DbAuthenticator 무변경). `oauth2_registered_client` SQL INSERT 금지(`repo.save()` 만).
<!-- 갱신 끝 -->
