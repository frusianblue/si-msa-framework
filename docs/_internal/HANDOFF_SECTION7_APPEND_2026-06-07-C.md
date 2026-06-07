# HANDOFF.md §7 append — 2026-06-07 (C): 이미지 태그 전략 B(불변 sha 주입)

> 본 파일은 다음 세션에서 `HANDOFF.md §7` 본문으로 병합한다(-B 와 동일 컨벤션). 병합 후 폐기.

## 세션5: 이미지 태그 전략 B 확정·구현 (devops)

### 배경(제거한 냄새)
직전 CI(`Jenkinsfile.kind`)는 ① 가변 `:dev` 핀(overlay) + ② `:dev`/`:<sha>` 양태그 push +
③ `apply -k` 후 `kubectl set image :<sha>` 명령형 덮어쓰기. 결과:
- **git↔클러스터 드리프트**: git 진실=`:dev`, 실제 파드=`:<sha>` (적용한 것 ≠ 뜨는 것).
- **죽은 `:dev`**: deploy 가 항상 sha 로 덮어 overlay 의 `:dev` 는 한 번도 지배하지 못함(push 낭비).
- **stale 잠복**: `set image` 가 빠지거나 수동 `apply -k` 시 `:dev`+`IfNotPresent` → 노드 캐시 옛 이미지 조용히 기동
  (과거 `:7e935d6` 손핀 어긋남과 동근원 = 수동/가변 핀의 증상).

### 결정(B) — 가변 :dev 폐기, 불변 sha declarative 주입(되커밋 없음)
- **단일 진실 = 불변 git-sha 태그.** apply 시점에 overlay 에 declarative 로 핀.
- overlay `images.newTag` = sentinel `__GITSHA__` → **미주입 apply 는 ImagePullBackOff(fail-loud)** = 조용한 stale 구조 차단.
- 주입 단일 지점 = 신규 헬퍼 `deploy/k8s/pin-image-tag.sh <overlay-dir> <tag>`(sed·멱등·태그형식 방어·kustomize 불요).
- **체크아웃 워크스페이스만 치환**: CI=잡 워크스페이스; 수동(03)=in-place→apply→작업트리 복원(overlay 는 `../../base`
  상대참조라 임시복사 apply 불가).

### 변경 파일
- `deploy/k8s/overlays/dev/kustomization.yaml` — newTag dev→`__GITSHA__`(4서비스) + 주석 재작성.
- `deploy/k8s/pin-image-tag.sh` — 신규 공용 주입 헬퍼.
- `deploy/cicd/Jenkinsfile.kind` — Build&Push `:<sha>` 단일(— `:dev`); Deploy `pin→apply -k`(— `set image`); 헤더 주석.
- `deploy/k8s/standalone-kind/03-dev-overlay-up.sh` — 불변 태그 push + 주입→apply→복원(— `:dev`).
- `deploy/k8s/standalone-kind/07-reboot-recover.sh` — 라이브 Deployment image ref 태그로 kind load(— 하드코딩 `:dev`).
- 문서: `docs/guide/PITFALLS.md` §9 ★항목 + 자가진단 2행; `docs/_internal/HANDOFF_SUMMARY.md`;
  `deploy/k8s/standalone-kind/README.md` 메모.

### 검증(저자환경, 오프라인)
- `bash -n` 3종 PASS. 헬퍼 치환/멱등/잘못된태그거부 PASS. 작업트리 sentinel 복원 확인. 코드경로 가변 `:dev` 0건.
- kustomize build(`kubectl kustomize`)·실배포는 받는 쪽 로컬(저자환경 kubectl 부재).

### 잔여/이월
- **prod overlay `:latest`** 동일 가변-태그 부채 — 차기 동일 sentinel/주입 전환.
- **Kaniko 다중빌드 미해결**(gateway 만 빌드) — 태그 전략과 독립, 다음 세션 #1(`NEXT_CI_KANIKO_MULTIBUILD.md`). push 는 이제 `:<sha>` 단일.
