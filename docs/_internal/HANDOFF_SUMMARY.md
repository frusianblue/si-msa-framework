# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ §S3' 2단계(최소 pull sanity) PASS — standalone kind 노드가 레지스트리 한정 이름 직접 pull 실증(2026-06-06 세션5).** `01-pull-sanity.sh` → node=sanity-worker 가 `reg.local/sanity/busybox:test` 를 Pulled 1건으로 직접 pull. **= Docker Desktop kind 에서 미러 인터셉트 500 으로 막혔던 동작이 standalone 에선 됨**(설계 가정 3개 성립: 점 있는 이름=레지스트리 인식 / certs.d extraMounts 노드 마운트 / push≠pull 이나 리포지토리 경로 동일=같은 블롭). 선행으로 **kind CLI 설치**(DD 내장 kind≠standalone CLI), **DD k8s 토글 OFF 무방**(엔진은 동작) 확인. **3단계 첫 조각 드롭**: `02-auth-pull-sanity.sh` — htpasswd 비공개 레지스트리(harbor.local) + `harbor-cred`(imagePullSecrets) → 노드 pull. docker-desktop kind 에선 도달층에서 막혀 못 봤던 *인증 경로*를 끝까지 검증(secret/SA 부착은 맞았으나 pull 자체가 안 됐었음). **결정 필요(받는 쪽)**: 4단계 레지스트리를 (A) 실제 Harbor 제품 vs (B) 02 의 인증 레지스트리로 충분(프레임워크 검증이 목적이면 바로 dev overlay apply) — 권장 (B).

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션5(pull sanity PASS·kind 설치·인증 sanity 드롭·4단계 레지스트리 fork)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 스크립트/문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| kind CLI 설치 | DD 내장 kind 가 standalone CLI 미설치 → `kind` 직접 설치(releases/latest). DD k8s OFF 무방(엔진 동작) 확인. |
| §S3' 2단계 PASS | `01-pull-sanity.sh` 실행 → ✅ node=sanity-worker, `reg.local/...` 직접 pull, Pulled 1건. 메커니즘 실증. |
| §S3' 3단계 첫 조각 | `02-auth-pull-sanity.sh`(htpasswd 비공개 레지스트리 harbor.local + harbor-cred imagePullSecrets→노드 pull) + `certs.d/harbor.local/hosts.toml` 드롭. 정적검증 통과(bash -n, toml parse). |
| 문서 동반 | `NEXT_K8S_REAL_DEPLOY.md`(2단계 PASS·3단계 첫 조각·4단계 fork) · `PITFALLS.md` §9 신규 3항 · `README.md`(02 추가·선행 설치). |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드 가동(01 통과). docker-desktop kind 는 k8s OFF(엔진만).
- **레지스트리**: 01=무인증 `kind-registry`(reg.local). 02=htpasswd `harbor-auth-reg`(harbor.local) — 받는 쪽 실행 대기.
- **이미지**: 실 si-msa/<svc>:dev 는 아직 standalone 레지스트리에 미push(4단계). dev overlay 핀=`:dev`.

## 바로 다음 할 일 (Next)
1. **`bash deploy/k8s/standalone-kind/02-auth-pull-sanity.sh`** — 비공개+인증 노드 pull 검증. ✅ PASS 면 dev overlay 인증 경로 유효.
2. **4단계 레지스트리 결정(A 실Harbor / B 인증레지스트리로 충분)** — 권장 B(프레임워크 검증 목적이면). B 면 실 si-msa/<svc>:dev 를 harbor.local(=harbor-auth-reg)에 push → `kubectl apply -k deploy/k8s/overlays/dev`.
3. **dev overlay apply 후 검증**: 6파드 Ready + Harbor Pull>0 + DB/admindb/파일저장(/tmp/uploads)/AS 토큰 스모크(`FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true`).
- 이후: S4 애드온 → S5 prod-rehearsal → S6 상위 흐름 → S7 Jenkins(sha 핀 자동 주입).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **Docker Desktop 내장 kind ≠ standalone `kind` CLI** — 별도 설치 필요(`FAIL: kind 가 PATH 에 없음`). DD k8s 토글은 꺼도 됨(엔진/kubectl 클라는 동작, 연결거부는 죽은 k8s API 친 것뿐).
- **standalone kind 노드 pull 됨(실증)** — certs.d + 점 있는 레지스트리 이름 + kind 네트워크 연결이면 레지스트리 한정 이름 직접 pull. Docker Desktop kind 의 미러 인터셉트는 standalone 에 없음.
- **certs.d 는 바인드마운트라 라이브 반영** — 클러스터 재생성 없이 ./certs.d 에 hosts.toml 추가하면 노드가 읽음(02 의 harbor.local 추가가 이걸 활용).
- **인증 경로는 imagePullSecrets(kubelet) + certs.d(도달층) 두 층** — harbor-cred 의 docker-server 키(harbor.local)가 이미지 레지스트리명과 일치해야 kubelet 이 cred 선택. http Basic 은 hosts.toml 이 http:// 여야 노드가 평문 전송.
- **이론 맹신 금지(유지)** — 02 PASS 가 4단계(실 이미지/overlay) 게이트.
- **ArgoCD/GitOps ≠ pull** — pull 은 언제나 노드 containerd.

<!-- 갱신 끝 -->
