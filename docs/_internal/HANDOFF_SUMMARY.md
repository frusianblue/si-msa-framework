# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**✅ kind 첫 배포 풀 그린 달성(2026-06-06) — kind(=Docker Desktop kind 모드, 3노드 v1.34.3) 위에 4서비스+PG+Redis 를 prod 프로파일로 올려 6파드 전부 `1/1 Running`.** compose 까지였던 검증선이 **kind 까지 확장**됐다. 실배포를 순차 트리아지하며 정합 결함 **3건**(① 이미지명 `newName` ② NetworkPolicy postgres allow ③ admindb initdb 1회성)을 발견·수정. AS `/actuator/health/readiness` UP · OIDC discovery(issuer `http://auth-server:9000`) · authdb 전 Flyway 테이블(oauth2_*·auth_signing_key·app_user·roles·framework_lock) · `app_user` 에 `tester` seed 까지 확인. **다음 섹션 = OAuth2 클라이언트 등록 → authorization_code+PKCE 토큰 플로우(=DbAuthenticator 운영 인증 경로 실증).** prod 에 등록 클라이언트 0건은 **설계**(LocalDemo `@Profile("local")` → prod 비활성). 착수 스펙 = `_internal/planning/NEXT_KIND_AUTH_TOKEN_FLOW.md`, 함정 `PITFALLS.md §9`.

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: kind 첫 배포 검증 완료 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — 스택 무변경.

## 직전에 한 것 (Done)
**kind 첫 배포 실행 → 6파드 그린.** 실배포 트리아지 3건(상세=PITFALLS §9):
| # | 증상 | 원인 | 수정(적용 형태) |
|---|---|---|---|
| 1 | `apply` 후 4앱 파드 `ImagePullBackOff` | `overlays/local` 의 `images:` 가 `newTag` 만 → 렌더명 `registry.example.com/...:local` 잔존(노드 적재명 `si-msa/...:local` 과 불일치) | `images.newName: si-msa/<svc>` (**overlay**) |
| 2 | auth/user/admin `CrashLoop`, Flyway `Connect timed out`(SQLState 08001), gateway 만 생존 | **Docker Desktop kind 는 NetworkPolicy 집행** + `default-deny-ingress` 에 postgres 수신 허용 규칙 부재 | `overlays/local/postgres.yaml` 에 `allow-postgres-from-apps` (**overlay**) |
| 3 | admin-service `CrashLoop`, `FATAL: database "admindb" does not exist`(3D000) | postgres initdb 는 **PGDATA 빈 최초 1회만** 실행 → admindb 줄 추가 전 떠 있던 PG 엔 미생성 | (즉시)`CREATE DATABASE admindb OWNER siuser` 또는 PG 재초기화. **신규 클러스터엔 initdb 가 자동 생성**(검증됨) |

- **환경 정정(중요)**: 핸드오프의 "kind 3노드"는 실제론 **Docker Desktop 의 내장 kind 모드**(컨텍스트 `docker-desktop`, 노드 `desktop-control-plane/worker/worker2`). `kind` CLI/`kind load` **불사용** — 노드가 `docker ps` 에 안 보이고(노드명≠컨테이너명), 이미지는 `desktop-containerd-registry-mirror` 가 로컬 docker 이미지를 자동 노출(테스트 파드 `si-msa/gateway:local` 1/1 Running 으로 실증) → **적재 단계 자체가 불필요**.
- 동반 문서 갱신: `PITFALLS §9`(+자가진단 행)·`LOCAL_K8S_TEST.md`(트러블슈팅)·`LOCAL_K8S_ENV_SETUP.md`·`K8S_ADDONS.md`(kind 집행 정정)·`base/common/networkpolicy.yaml` 주석·`overlays/local/postgres.yaml`/`kustomization.yaml`.

## 현재 상태 (적용/검증)
- **✅ kind 첫 배포 = 받는 쪽 검증 완료**(이번 세션): si-msa ns 6파드(gateway/auth-server/user-service/admin-service/postgres/redis) 전부 `1/1 Running`, RESTARTS 0. AS readiness UP·discovery 정상·authdb Flyway 전 테이블·`app_user` `tester` 1행.
- **✅ compose = 그린(회귀용)**.
- **🟡 미실증 = OAuth2 클라이언트 등록 → 토큰 플로우**: prod 클라이언트 0건(LocalDemo `@Profile("local")`, **설계**)이라 authorization_code 진입 불가. DbAuthenticator 는 **데이터·부팅까지** 확인, **실 로그인(인증 경로)** 은 클라이언트 등록 후 검증 예정.
- **현재 인프라**: Docker Desktop kind Active(3노드 `desktop-*`, v1.34.3), si-msa ns 6파드 Running. compose 정지 권장(8000/8080/8081/9000 포트 충돌 방지). 재현 한 방 = `kubectl delete -k … → apply -k …`(신규 PG 라 initdb 가 admindb 까지 자동 생성 — 검증됨).
- 작성환경 Maven Central·릴리스 CDN 차단 → 빌드/kubectl 직접 실행 불가(정적 작성 + 받는 쪽 실행).

## 바로 다음 할 일 (Next) — OAuth2 클라이언트 등록 → 토큰 플로우 (스펙 `_internal/planning/NEXT_KIND_AUTH_TOKEN_FLOW.md`)
> prod 클라이언트 0건은 설계(프로젝트 책임). authorization_code 를 태우려면 클라이언트 등록이 선행.
1. **(권장) prod-안전 smoke/demo 클라이언트 시더 추가** — `@Profile("local")` 아닌 별도 플래그(예: `framework.auth.seed-smoke-client`, 기본 false)로 가드 → **DbAuthenticator 불변**, `RegisteredClientRepository.save()` 로 public+PKCE 클라이언트 등록. ⚠️ **SQL INSERT 수동 등록 금지** — `client_settings`/`token_settings` 가 SAS 전용 Jackson(`@class` 타입 메타) JSON 이라 reader 가 역직렬화에서 깨진다. 반드시 `repo.save()`. 로드맵 `demo-rp`(confidential) 등록과 겹치므로 출발점으로 재사용.
2. **(대안/빠른 확인) 클라이언트 없이 DbAuthenticator 만**: `/login` 폼 POST(CSRF 추출)로 `tester`/`Test1234!` 인증 302 확인(토큰 X, 인증 O).
3. **스모크 마감**: authorization_code+PKCE → 토큰 → DbAuthenticator 운영 경로 실증. issuer 가 in-cluster 명(`http://auth-server:9000`)이라 브라우저 redirect 는 port-forward 기준 보정 필요.
- 참고: kind 절차/트러블슈팅 `docs/ops/LOCAL_K8S_TEST.md` §8, 애드온(metrics-server/Prometheus/ingress) `docs/ops/K8S_ADDONS.md`, RP 연계(완료) `_internal/planning/NEXT_RP_IDTOKEN_LINK.md`.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것 — 전부 PITFALLS §9)
- **환경: 핸드오프 "kind" = Docker Desktop kind 모드.** 노드가 `docker ps` 에 안 보임(노드명≠컨테이너명) → `docker exec <node>`/`kind load` 불가. 이미지는 `desktop-containerd-registry-mirror` 가 호스트 docker 이미지를 자동 노출(테스트 파드로 확인) → 적재 불필요. `kind create cluster` 동시 사용 금지(충돌).
- **로컬 overlay 는 이미지 name 까지 노드 적재명과 1:1**: `images:` `newTag` 만으론 name(`registry.example.com/...`) 잔존 → `ImagePullBackOff`. `newName` 으로 short name 통일. 점검: `kubectl -n si-msa get deploy -o jsonpath='{..image}'`.
- **Docker Desktop kind 는 NetworkPolicy 집행**(standalone kindnet 비집행과 다름): 인-클러스터 의존(postgres 등)마다 `default-deny` 뚫는 allow 1:1 필요. 증상이 `Connect timed out`/08001 이면 인증·DNS 가 아니라 L3/4 차단 신호.
- **initdb 는 PGDATA 빈 최초 1회만**: 기존 PG 에 DB/스키마 추가는 자동 반영 안 됨 → 재초기화(휘발 PG=`rollout restart`) 또는 수동 `CREATE`. `database "X" does not exist`(3D000)가 신호.
- **prod 클라이언트 0건 = 정상**: `LocalDemo` `@Profile("local")`. prod OAuth2 클라이언트 등록은 프로젝트 책임(다음 섹션).
- (이전 세션 유지) AS 자체 체인엔 `/actuator/**` permitAll 직접 / 하드닝 컨테이너 쓰기경로 `/tmp` / `token-store.type` 과 모듈 의존 쌍 / "앱 로그 정상 ≠ healthy" / 멀티서비스는 실제로 띄워봐야 정합 결함 드러남.
<!-- 갱신 끝 -->
