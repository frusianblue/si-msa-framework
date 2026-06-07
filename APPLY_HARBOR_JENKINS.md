# APPLY — 인-클러스터 Harbor + Jenkins + 파이프라인 (standalone kind) · 세션3 통합본

레포 루트에서:  `unzip -o si-msa-harbor-jenkins-cicd.zip`
(코드/스크립트 + 갱신 문서 일체. 문서는 `docs/` 하위 그대로 덮어쓰기.)

## 실행 순서 (PASS 게이트 단위)
### Stage A — 영구 pull/재부팅 수정 (먼저, 고신뢰)
    kubectl --context kind-sanity apply -f deploy/k8s/standalone-kind/registry-trust-daemonset.yaml
    kubectl --context kind-sanity apply -k deploy/k8s/overlays/dev
    bash deploy/k8s/standalone-kind/07-reboot-recover.sh
### Stage B — 인-클러스터 Harbor
    bash deploy/k8s/standalone-kind/08-harbor-install.sh
### Stage C — 인-클러스터 Jenkins + 파이프라인
    bash deploy/k8s/standalone-kind/09-jenkins-install.sh
    # UI(port-forward 8088) → Pipeline 'si-msa-cd' (SCM=레포, Script Path=deploy/cicd/Jenkinsfile.kind) → Build Now

## 호스트 접속
- 현재: port-forward (`kubectl -n harbor port-forward svc/harbor 8080:80`, `kubectl -n jenkins port-forward svc/jenkins 8088:8080`)
- 다음(B안, 포워딩 졸업): docs/_internal/planning/NEXT_INGRESS_HOST_ACCESS.md (재생성+ingress-nginx+harbor.local/jenkins.local)

## 갱신 문서
- docs/ops/STANDALONE_KIND_HARBOR_JENKINS.md   (런북 — 호스트 접속 절 포함)
- docs/_internal/HANDOFF.md                    (§7 이번 세션 항목 추가)
- docs/_internal/HANDOFF_SUMMARY.md            (세션3 한 장 — B안 결정)
- docs/_internal/planning/NEXT_INGRESS_HOST_ACCESS.md  (다음 세션 B안 계획)
- docs/guide/_PITFALLS_APPEND_2026-06-07.md    (PITFALLS §8/§9 수동 병합용)
