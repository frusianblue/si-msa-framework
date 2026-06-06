# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🧹 문서 정합화 세션(2026-06-06) — master 트리 기준으로 SUMMARY·GAP_AUDIT·planning 분류를 재정렬하고, 보충 갭 목록을 코드 실측으로 확정.** 직전 SUMMARY/planning 이 master 트리보다 뒤처져 끝난 작업이 "다음 할 일"로 읽히던 꼬임을 정리. **보충 갭(A1~A9·B) 전수 코드 대조 결과: 완료=A1·A4·A9, 남은 실제 보충=A2·A3·A5·A6·A7·A8·B(7개).** kind "✅ 첫 배포"가 가리던 미실증 범위(dev/prod overlay·애드온·상위 흐름·운영 태그/볼륨)를 정직화하고, 다음 단계 = **실배포 검증 + 애드온**(신규 스펙 `NEXT_K8S_REAL_DEPLOY.md`).

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 문서 정합화 세션
- 대상 브랜치: master(`241e350`) · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(문서만 변경).

## 직전에 한 것 (Done)
- **갭 목록 정합화** — `GAP_AUDIT.md` 상단에 `⭐ 2026-06-06 재감사(코드 실측)` 정본 블록 추가 + 본문 A1 마커를 완료로 보정. A1(WebAuthn) 완료 반영, A4·A9 완료 재확인, A2/A3/A5/A6/A7/A8/B 열림 확인.
- **kind 검증범위 정직화** — `GAP_AUDIT §k8s 실배포 검증 범위`: 실증=`overlays/local` 6파드 + DbAuthenticator 토큰 플로우뿐. 미실증=dev/prod overlay·애드온(metrics-server/Prometheus/ingress)·상위 인증 흐름·운영 태그(`:local`+`IfNotPresent`)·볼륨(`/tmp` emptyDir).
- **다음 단계 스펙 신설** — `planning/NEXT_K8S_REAL_DEPLOY.md`: ①불변 이미지 태그+실 레지스트리 ②볼륨/영속(PVC vs S3, 운영 DB/redis 외부화) ③dev/prod overlay 실 apply ④애드온 클러스터 실증 ⑤상위 흐름 port-forward 스모크.
- **SUMMARY 재작성** — master 트리 기준 정합(이번 + 직전 세션).

## 현재 상태 (적용/검증)
- **master 트리 정합 확인됨**: `planning/` NEXT 스펙 7개 전부 구현 완료, 모듈 README 37/37(`실전 사용 예`, 펜스 균형 OK, Jackson 3 위반 0), k8s `deploy/`(kustomize base+overlays + ingress/networkpolicy/pdb/servicemonitor/hpa/hardening + docker/compose + Jenkinsfile/ci-cd.yml) 존재.
- **보충 갭 코드 실측(이번)**: A1 `framework-webauthn`(11 java, 체크리스트 4종) · A4 `RedisConcurrentSessionService`(Lua) · A9 `GatewayAuthProperties.audiences`+`GatewayJwksTokenVerifier` = 완료. A2 IdP-initiated만(SP-initiated 부재) · A3 `AesSigningKeyCipher`만 · A5 `ZipArchiver`만 · A6 단순 putObject · A7 core 부재 · A8 모듈 0 · B LoginAttempt jdbc 부재 = 열림.
- **문서 변경만**: 코드/스택 무변경.

## 바로 다음 할 일 (Next)
1. **실배포 검증 + 애드온** — `NEXT_K8S_REAL_DEPLOY.md` 착수. 순서: 이미지 태그 정책(불변+레지스트리) → 볼륨/영속(PVC or S3) → dev/prod overlay 실 apply → 애드온(metrics-server/HPA → Prometheus → ingress) → 상위 흐름 스모크. **결정 필요**: 태그 소스(GIT_SHA vs semver)·레지스트리 좌표·업로드 영속(PVC vs S3)·운영 DB/redis 외부화 여부.
2. **보충 갭(코드)** — 별도 트랙. 우선순위: A2(SP-initiated SLO)·A3(KMS/Vault) 각 독립 세션. A5~A8·B 선택 백로그.
   - 1-B. **planning housekeeping(`git mv`)** — 완료 스펙 archive 이동: KIND_AUTH_TOKEN_FLOW·LOCAL_COMPOSE_AND_KIND·OIDC_ID_TOKEN·RP_IDTOKEN_LINK·SIGNING_KEY_ROTATION·**NEXT_WEBAUTHN(A1 완료)**. `NEXT_SSO`는 6.2-B(=A2) 보유로 planning 유지(또는 6.2-B 추출 후 archive).

## 이번 세션 원칙 (되돌리지 말 것)
- **상태 확정은 master 트리(파일 실재) 우선** — SUMMARY/planning 텍스트·배너는 lag 가능. 끝난 작업 재구현 금지.
- **"kind ✅"는 `local`+auth 까지** — 운영 준비(dev/prod·애드온·불변태그·PVC)는 `NEXT_K8S_REAL_DEPLOY.md` 로 별도 검증.
- **갭 정본은 `GAP_AUDIT.md` 상단 재감사 블록** — 붙여넣기로 떠도는 구버전 목록(A4·A9 미완 표기) 신뢰 금지.
<!-- 갱신 끝 -->
