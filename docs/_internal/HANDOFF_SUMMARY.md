# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ §S3' standalone kind 운영 리허설 = 본체 그린(6파드 Running), AS 토큰 1줄만 잔여(2026-06-06 세션 종료).** Docker Desktop kind 미러 인터셉트 BLOCKED → standalone `kind` CLI 트랙으로 전환해 빌드→harbor.local 인증 노드 pull→prod 부팅→DB(authdb/sidb/admindb)→**6파드 1/1 Running RESTARTS 0** 전 구간 그린. 산출물 `deploy/k8s/standalone-kind/`(kind-config·certs.d 2·01~03·00-cleanup·README). 결정 **B**(인증 레지스트리로 충분, Harbor 제품 미설치). 4단계 결함 3건 수정: ① default SA 레이스(AlreadyExists)→imagePullSecrets 를 앱 파드 템플릿(component=service)에 직접 주입 ② dev overlay=Spring prod 프로파일이라 placeholder AES/JWT 가드 **차단**→가드 통과값 교체 ③ gateway 만 생존(프로파일 미지정=경고만)이 진단 단서. **잔여 1**: AS client_credentials 토큰 — wrong-secret→**401 로 인증 메커니즘 정상 확인**(앞 302 는 stale port-forward 아티팩트), 깨끗한 port-forward 로 access_token 1줄 확인만 남음. **모든 작업 uncommitted → 다음 세션 첫 행동 = commit/push(그린 박제).**

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션(§S3' standalone kind 그린·결함3건·토큰 잔여)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 스크립트/매니페스트/문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| standalone kind 트랙 | `deploy/k8s/standalone-kind/` 신설(kind-config 3노드+config_path+certs.d extraMounts, hosts.toml reg.local/harbor.local, 01 무인증 pull ✅PASS, 02 인증 pull ✅PASS, 03 dev-overlay up, 00 cleanup, README). 정적검증(bash -n·yaml·toml). |
| 4단계 dev overlay 그린 | 빌드→push(localhost:5443→harbor.local)→`apply -k overlays/dev`→**6파드 1/1 Running RESTARTS 0** + authdb/sidb/admindb 분리 확인 ✅. |
| 결함 3건 수정 | (a) default SA AlreadyExists→imagePullSecrets 파드템플릿 주입(`pull-secret-dev.yaml` SA 제거 + `kustomization.yaml` patch) (b) prod 프로파일 시크릿 가드 차단→`secrets-dev.yaml` AES/JWT 가드 통과값(점/길이, DB 비번 유지) (c) gateway 생존 단서. |
| 함정/문서 | PITFALLS §9 신규 5건(레지스트리 점·certs.d 콜론·DD kind≠CLI·default SA 레이스·prod 프로파일 시크릿가드) + 자가진단표. HANDOFF §7·HANDOFF_SUMMARY·NEXT_K8S_REAL_DEPLOY 갱신. |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. dev overlay apply 됨, 6파드 Running. docker-desktop kind 는 k8s OFF(엔진만). 잔존 컨테이너: `kind-registry`(01 무인증, 정리 가능)·`harbor-auth-reg`(harbor.local, 유지).
- **이미지**: harbor-auth-reg 에 `si-msa/<svc>:dev` 4개 push 됨(노드 Pulled 11건). overlay 핀=`:dev`.
- **그린 기준**: 6파드 ✅ · DB 3개 ✅ · AS access_token = **미확인(stale forward 로 302 났던 것, 깨끗이 재시도 필요)**.

## 바로 다음 할 일 (Next)
1. **commit/push 먼저** — 누적분(standalone-kind 6파일 + dev overlay 2수정 + PITFALLS/HANDOFF/NEXT). 그린 박제.
   `git add -A && git commit -m "feat(devops): standalone-kind 운영경로 리허설 그린 + dev overlay 수정(imagePullSecrets 파드주입·prod 시크릿가드 통과값)" && git push origin master`
2. **AS 토큰 1줄 마감** — port-forward 정리 후 깨끗이: `pkill -f port-forward; kubectl -n si-msa port-forward svc/auth-server 9000:9000 >/tmp/pf.log 2>&1 & sleep 4; curl -s -u demo-service:demo-secret -d grant_type=client_credentials -d scope=api.read localhost:9000/oauth2/token | head -c 500; echo`. `access_token` 나오면 §S3' 완전 종료. (안 나오면 SS DEBUG 로그 — 단 wrong→401 확인됐으니 가능성 낮음.)
3. **S4 애드온** — metrics-server(HPA, kind 는 `--kubelet-insecure-tls`) → kube-prometheus-stack(설치 후 dev overlay ServiceMonitor `$patch:delete` 해제 → dev 도 관측). 이후 S5 prod-rehearsal → S6 상위흐름(OIDC RP·이중발급기) → S7 Jenkins(sha 핀 자동 주입).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **default SA 재생성 금지** — fresh ns 와 같은 apply 면 AlreadyExists 레이스 → imagePullSecrets 미부착(401). 앱 파드 템플릿에 직접 주입(component=service).
- **dev overlay=Spring prod 프로파일** — placeholder 시크릿(change-me/change-this, 짧음)은 AES/JWT 가드가 **차단**(경고 아님). secrets-dev 값은 가드 통과값이어야. DB 비번은 postgres initdb 사용자와 묶여 변경 금지.
- **standalone kind=NetworkPolicy 비집행** — apps→postgres 안 막힘(docker-desktop kind 와 정반대). · **certs.d 점/콜론 규칙** · **DD 내장 kind≠standalone kind CLI**.
- **wrong-secret→401 = 인증 메커니즘 정상** — 302 는 stale port-forward 아티팩트. 깨끗이 한 번만 찍을 것.
- **이론 맹신 금지 + ArgoCD/GitOps≠pull**(pull 은 언제나 노드 containerd) 유지.

<!-- 갱신 끝 -->
