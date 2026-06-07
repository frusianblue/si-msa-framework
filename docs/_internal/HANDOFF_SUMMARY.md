# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🟢 CI 1차 완주 근접 — 빌드/push 완전 그린, deploy 2종 결함 수정(2026-06-07 세션5–7, devops).** (세션5) **태그 전략 B**: 가변 `:dev`+명령형 `set image` 폐기 → 불변 git-sha 단일 태그 declarative 주입(sentinel `__GITSHA__`+`pin-image-tag.sh`). (세션6) **Kaniko 다중빌드**: 서비스별 kaniko 컨테이너 4개+순차 executor 1회. **→ 실잡에서 4서비스 전부 build→push 성공(Harbor `gateway/auth-server/user-service/admin-service:e46445c` + cache 17, 빌더 캐시 재사용 실증).** (세션7) **deploy 단계 2종 결함 수정**: ① `jenkins-deployer` 가 클러스터 스코프 `namespaces` patch 불가(`edit` 는 ns 한정) → **ClusterRole+Binding(namespaces get/patch, resourceNames=si-msa)** 추가. ② dev overlay 가 ServiceMonitor(operator CRD)를 그대로 apply → operator 미설치 클러스터에서 `no matches for kind "ServiceMonitor"` → **dev 도 local 처럼 SM `$patch:delete`**, 관측은 **05 가 base SM 직접 apply**(단일 소유자). 오프라인 검증: bash -n, kustomize patch 구조(7개·SM delete 포함), RBAC 6객체 파싱 PASS. **받는 쪽: jenkins-rbac.yaml admin 재apply + 코드 push 후 CI 재실행 → 6파드 rollout 확인.**

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션5–7(태그 B + Kaniko 다중빌드 + CI deploy RBAC/SM 수정)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 미커밋(누적 + 세션4 + 세션5–7; 세션5–6 은 직전에 push 되어 CI 가 그걸로 빌드 성공).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| (세션5) 태그 B | overlays/dev sentinel `__GITSHA__` + `pin-image-tag.sh`(신규) + Jenkinsfile/03/07 정합. |
| (세션6) Kaniko | Jenkinsfile.kind podTemplate kaniko 1→4 + 서비스별 container 순차 executor. **실잡 4서비스 push 성공.** |
| (세션7) ns RBAC | jenkins-rbac.yaml 에 ClusterRole+ClusterRoleBinding(`namespaces` get/patch, resourceNames=si-msa). |
| (세션7) SM 분리 | dev overlay SM `$patch:delete`(코어 apply operator 비의존) + 05 가 base SM 직접 apply + 사전점검 경고 반전. |
| 검증(오프라인) | bash -n(05)·dev overlay patch 7개(SM delete 포함)·images sentinel 유지·jenkins-rbac 6객체 파싱. |
| 문서 | PITFALLS §9 ★ deploy 2종 항목 + 자가진단 2행 갱신/추가 · 이 SUMMARY · HANDOFF §7 append(-C 태그/-D Kaniko/-E deploy수정). |

## 현재 상태 (적용/검증)
- **빌드/push**: ✅ 그린(4서비스 :e46445c, cache 재사용). Kaniko 다중빌드·태그 전략 실증 완료.
- **deploy**: 2종 결함 수정 완료(파일). **받는 쪽이 (a) jenkins-rbac admin 재apply (b) 코드 push (c) CI 재실행** 하면 6파드 rollout 예상.
- **클러스터**: standalone kind-sanity. operator(kube-prometheus-stack) 미설치 상태에서도 코어 deploy 가능(SM 제거).
- **커밋**: 세션5–6 push 됨(CI 빌드 근거). 세션7 + 누적 백로그 **미커밋**.

## 바로 다음 할 일 (Next)
1. **CD 완주(최우선)** — ① admin 컨텍스트로 `kubectl apply -f deploy/k8s/standalone-kind/jenkins-rbac.yaml`(또는 09 재실행) → 새 ClusterRole 적용. ② 세션7 코드 commit&push. ③ `si-msa-cd` Build Now → apply 그린 → 6파드 `:<sha>` rollout 확인. (관측 원하면 별도 `05-prometheus-stack.sh`.)
2. **prod overlay `:latest` → sentinel/주입 전환**(가변-태그 부채 청산).
3. **commit/push 누적분 정리**(그린 박제).
4. **정리** — `NEXT_CI_KANIKO_MULTIBUILD.md` archive(해결), HANDOFF §7 append(-C/-D/-E) 본문 병합.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **CI 배포 SA 는 자기 ns 객체(클러스터 스코프)에 명시적 ClusterRole 필요** — namespaced `edit` 으론 Namespace patch 못 함. `resourceNames`+ClusterRole 로 최소권한. **클러스터 스코프 RBAC 생성은 admin 이 해야**(SA 자신은 못 만듦).
- **옵셔널 operator CRD 의존 리소스(ServiceMonitor)는 코어 배포에 넣지 않는다** — add-on 스크립트(05)가 단독 소유. 관측은 배포의 선결조건이 아니다. dev·local 둘 다 SM `$patch:delete`.
- **"네임스페이스 없음" ≠ 실제 에러** — `cannot patch resource "namespaces"` 는 **권한**(Forbidden), ns 는 09 가 선생성돼 존재. 에러 문구 정독.
- **무엇이 뜨는가 = 불변 태그 declarative 핀**(세션5). **Kaniko = 컨테이너당 executor 1회·순차**(세션6, 공유 builder 캐시 보존).
- **써머리 위치 = `docs/_internal/HANDOFF_SUMMARY.md`**. 배치 트랙 = `HANDOFF_BATCH_SUMMARY.md` 독립.

<!-- 갱신 끝 -->
