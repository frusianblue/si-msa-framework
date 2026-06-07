# HANDOFF.md §7 append — 2026-06-07 (D): Kaniko 다중빌드 = 서비스별 컨테이너 분리

> 다음 세션에서 `HANDOFF.md §7` 본문으로 병합(-C 와 함께). 병합 후 폐기.

## 세션6: Kaniko 다중빌드 해결 (devops)

### 문제(직전 세션 진단)
`Jenkinsfile.kind` 가 단일 `kaniko` 컨테이너에서 4서비스를 `.each { sh "executor …" }` 로 빌드 →
**gateway 만 성공**, 2번째 `sh` 가 `Process exited immediately after creation`.
원인: **kaniko executor 가 빌드 중 자기 컨테이너 루트FS(/)를 대상 이미지로 덮어씀 → /busybox/sh 클로버** →
다음 sh 스텝이 셸을 못 띄움. (Jenkinsfile 파일을 4개로 쪼개도 같은 컨테이너면 동일 실패 — 파일 문제가 아님.)

### 해결
- **서비스별 kaniko 컨테이너 4개**(`kaniko-gateway/-auth-server/-user-service/-admin-service`)를 podTemplate 에 두고,
  각 `container("kaniko-${svc}")` 블록에서 executor **1회만** 실행 → 컨테이너가 다르면 셸도 매번 새것 → 클로버-재사용 없음.
- **순차(병렬 금지)**: `Dockerfile.build` builder 스테이지(SERVICE 무관, 4 bootJar 한 번에)는 순차면 첫 빌드만 풀 Gradle,
  나머지 3개는 `--cache-repo`(레지스트리)에서 builder 레이어 재사용. 병렬이면 4개 동시 cache-miss → 풀빌드 4회(느림+메모리).
- push 는 **`:<git-sha>` 단일 태그**(세션5 태그 전략과 정합). deploy 단계는 세션5 그대로(`pin-image-tag.sh`→`apply -k`).
- 대안(미채택): 단일 kaniko + `--cleanup` + 단일 sh 루프 — 컨테이너 1개로 가볍지만 `--cleanup` 안정성 편차.

### 변경 파일
- `deploy/cicd/Jenkinsfile.kind` — podTemplate(kaniko 컨테이너 1→4 + kubectl) + Build&Push 스테이지(서비스별 container 블록 순차).
- 문서: `docs/guide/PITFALLS.md` §9 ★항목 + 자가진단 1행; `docs/_internal/HANDOFF_SUMMARY.md`.

### 검증(저자환경, 오프라인)
- 임베디드 pod YAML 파싱 OK(컨테이너 5개: kaniko×4 + kubectl). Groovy 중괄호/괄호/대괄호 균형 OK. 빌드 루프↔컨테이너명 매핑 OK.
- 실제 잡 실행(4서비스 build→push→rollout)·캐시 재사용 실측은 **받는 쪽 로컬**(저자환경 Jenkins/kind 부재).

### 잔여/이월
- **실클러스터 완주**: `si-msa-cd` 재실행 → 4서비스 `:<sha>` push → `apply -k`(주입) → 6파드 rollout 그린 확인.
- **prod overlay `:latest`** → sentinel/주입 전환(세션5 이월).
- **commit/push 누적분** 정리(세션2 S5 이후 전부 미커밋).
- 서비스 추가 시 podTemplate 컨테이너 + 빌드 루프 리스트 **동시 갱신** 규약(둘 중 하나만 빠지면 container-not-found/빌드 누락).
