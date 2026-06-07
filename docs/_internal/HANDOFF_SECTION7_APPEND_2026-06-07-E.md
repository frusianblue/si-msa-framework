# HANDOFF.md §7 append — 2026-06-07 (E): CI deploy 단계 2종 결함 수정(ns RBAC + ServiceMonitor)

> 다음 세션에서 `HANDOFF.md §7` 본문으로 병합(-C/-D 와 함께). 병합 후 폐기.

## 세션7: CI deploy 단계 (devops)

### 맥락
세션6 의 Kaniko 다중빌드 수정 후 실잡(`si-msa-cd`) 가동 → **빌드/push 완전 성공**
(Harbor 에 gateway/auth-server/user-service/admin-service `:e46445c` + cache 17 = 빌더 캐시 재사용 실증).
이어진 **Deploy 단계**에서 2종 결함으로 중단.

### 결함 ① — jenkins-deployer 가 Namespace patch 불가(Forbidden)
- 증상: `namespaces "si-msa" is forbidden: User "system:serviceaccount:jenkins:jenkins-deployer" cannot patch resource "namespaces"`.
- **"ns 없음"이 아니다** — 09-jenkins-install.sh 가 si-msa ns 를 선생성(존재). `apply -k` 가 base 의 Namespace/si-msa 에 3-way merge patch 를 걸 때 권한 부족.
- 원인: jenkins-rbac 의 `edit` 는 **RoleBinding(네임스페이스 한정)** → 클러스터 스코프 Namespace 엔 미적용.
- 해결: jenkins-rbac.yaml 에 **ClusterRole+ClusterRoleBinding** 추가 — `namespaces` get/patch, `resourceNames: [si-msa]`(그 한 객체만 = 최소권한). 생성은 09 admin 이 선보장 → CI 는 patch 만 필요.
- ⚠️ **클러스터 스코프 RBAC 생성은 cluster-admin** 이 해야 한다 → 받는 쪽이 admin 컨텍스트로 `kubectl apply -f jenkins-rbac.yaml`(또는 09 재실행). jenkins-deployer 자신은 ClusterRole 을 못 만든다.

### 결함 ② — ServiceMonitor CRD 가 코어 apply 를 막음
- 증상: `no matches for kind "ServiceMonitor" in version monitoring.coreos.com/v1 — ensure CRDs are installed first`.
- 원인: `base/common/servicemonitor.yaml` 의 SM 은 Prometheus Operator(kube-prometheus-stack) 설치돼야 존재하는 CRD. dev overlay 가 이전에 "05 먼저 실행" 전제로 SM `$patch:delete` 를 빼둔 상태 → operator 미설치 클러스터(CI/standalone 기본)에서 apply 전체 실패.
- 해결: **dev overlay 도 local 과 동일하게 SM `$patch:delete`** → 코어 apply(CI·수동·standalone)는 operator 비의존. **관측의 SM 단일 소유자 = 05-prometheus-stack.sh** — operator 설치 후 `kubectl -n si-msa apply -f deploy/k8s/base/common/servicemonitor.yaml` 직접(dev overlay 재적용 의존 제거). 05 사전점검 경고도 반전(이제 delete 패치 존재가 정상).

### 변경 파일
- `deploy/k8s/standalone-kind/jenkins-rbac.yaml` — ClusterRole/ClusterRoleBinding(namespaces) 추가.
- `deploy/k8s/overlays/dev/kustomization.yaml` — SM `$patch:delete` 추가(주석 재작성).
- `deploy/k8s/standalone-kind/05-prometheus-stack.sh` — base SM 직접 apply + 사전점검 경고 반전 + 헤더 주석.
- 문서: `docs/guide/PITFALLS.md` §9 ★항목 + 자가진단 2행; `docs/_internal/HANDOFF_SUMMARY.md`.

### 검증(저자환경, 오프라인)
- bash -n(05). dev overlay kustomize patch 구조 파싱(7개, SM delete 포함). jenkins-rbac 멀티문서 6객체 파싱. images sentinel 유지.
- 실 apply(6파드 rollout)·SM 스크랩은 받는 쪽 로컬.

### 받는 쪽 액션(순서)
1. admin 컨텍스트: `kubectl apply -f deploy/k8s/standalone-kind/jenkins-rbac.yaml` (새 ClusterRole/Binding 적용).
2. 세션7 코드 commit & push(CI 가 git master 의 overlay/Jenkinsfile 사용).
3. `si-msa-cd` Build Now → deploy 그린 → `kubectl -n si-msa get pods` 6파드 Running + `get deploy -o jsonpath='{..image}'` 가 전부 `harbor.local/si-msa/*:<sha>`.
4. (옵션) 관측: `bash deploy/k8s/standalone-kind/05-prometheus-stack.sh`.
