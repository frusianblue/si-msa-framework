# APPLY — 인-클러스터 Harbor + Jenkins + 파이프라인 (standalone kind)

레포 루트에서:  `unzip -o si-msa-harbor-jenkins-cicd.zip`

## 실행 순서 (PASS 게이트 단위 — 한 단계씩 통과 후 다음)

### Stage A — 영구 pull/재부팅 수정 (먼저, 고신뢰)
    kubectl --context kind-sanity apply -f deploy/k8s/standalone-kind/registry-trust-daemonset.yaml
    kubectl --context kind-sanity apply -k deploy/k8s/overlays/dev     # base imagePullPolicy 반영
    bash deploy/k8s/standalone-kind/07-reboot-recover.sh
    # PASS: si-msa 6파드 Ready. 재부팅 후엔 07 한 줄이 복구 루틴.

### Stage B — 인-클러스터 Harbor
    bash deploy/k8s/standalone-kind/08-harbor-install.sh
    # PASS: harbor ns 파드 Running + PVC Bound. 포털 port-forward 로 si-msa(public) 확인.

### Stage C — 인-클러스터 Jenkins + 파이프라인
    bash deploy/k8s/standalone-kind/09-jenkins-install.sh
    # → UI(port-forward 8088) New Item=Pipeline 'si-msa-cd'
    #   Pipeline script from SCM, Git=레포, Branch=*/master, Script Path=deploy/cicd/Jenkinsfile.kind
    #   Save → Build Now
    # PASS: Checkout→Kaniko build&push→Deploy 그린. Harbor 에 4 repo×(<sha>,dev), :<sha> 롤아웃.

자세한 설계/트러블슈팅: docs/ops/STANDALONE_KIND_HARBOR_JENKINS.md
PITFALLS 병합분: docs/guide/_PITFALLS_APPEND_2026-06-07.md (수동 병합)
