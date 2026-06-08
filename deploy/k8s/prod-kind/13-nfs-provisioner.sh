#!/usr/bin/env bash
# deploy/k8s/prod-kind/13-nfs-provisioner.sh
# ─────────────────────────────────────────────────────────────────────────────
# kind-svc 애드온: NFS 동적 프로비저너(RWX 제공). 파일 영속(components/file-storage-nfs)의 선결조건.
#
#   왜 NFS인가: user/admin 은 replicas:2 → 두 파드가 같은 업로드 볼륨을 공유해야 한다(RWX).
#     local-path 등 RWO StorageClass 는 단일 파드 전용 → 둘째 파드가 ContainerCreating 에서 멈춘다.
#     RWX 를 주는 가장 단순·안전한 길 = nfs-server-provisioner(built-in ganesha NFS).
#   왜 in-cluster ganesha인가: ganesha 는 userspace NFS → 호스트 커널 `nfsd` 모듈 불요(WSL2 안전).
#     K8s 밖 도커 NFS(nfsd 커널 의존)는 WSL2 에서 까다로움. 운영은 관리형 NFS/EFS 로 promote.
#   위치: 12(register) 다음, 20(bootstrap) 전 — PVC(prod overlay)가 sync 될 때 SC `nfs` 가 이미 있어야 Bound.
#         (애드온은 ArgoCD 밖 — Harbor/ingress 와 동일하게 스크립트로 설치. PVC/앱만 GitOps.)
# 실행: bash deploy/k8s/prod-kind/13-nfs-provisioner.sh
# 멱등: helm upgrade --install. 재실행 시 값 갱신.
# ⚠️ 이미지 pull: registry.k8s.io/sig-storage/nfs-provisioner — kind-svc 노드가 pull.
#    Docker Desktop mirror intercept(PITFALLS §8) 환경이면 standalone kind containerdConfigPatches 확인.
# ⚠️ values 키는 chart 버전에 따라 다를 수 있다 — 적용 전 `helm show values <chart>` 로 확인 권장.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
SVC_CTX="${SVC_CTX:-kind-svc}"
NS="${NFS_NS:-nfs-provisioner}"
SC="${SC:-nfs}"
RELEASE="${RELEASE:-nfs-server-provisioner}"
REPO_NAME="nfs-ganesha-server-and-external-provisioner"
REPO_URL="https://kubernetes-sigs.github.io/nfs-ganesha-server-and-external-provisioner/"
CHART="$REPO_NAME/nfs-server-provisioner"
# ganesha server 의 /export 백킹 볼륨(영속) — kind 기본 SC. 여기 저장된 게 RWX export 의 실데이터.
BACKING_SC="${BACKING_SC:-local-path}"
BACKING_SIZE="${BACKING_SIZE:-10Gi}"

echo "== 0) 전제 =="
for b in helm kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kubectl --context "$SVC_CTX" get nodes >/dev/null 2>&1 \
  || { echo "FAIL: $SVC_CTX 접근 불가 — 1단계(인프라) 선행"; exit 1; }

echo "== 1) helm repo =="
helm repo add "$REPO_NAME" "$REPO_URL" >/dev/null 2>&1 || true
helm repo update "$REPO_NAME" >/dev/null

echo "== 2) nfs-server-provisioner 설치(멱등) — StorageClass=$SC, 백킹=$BACKING_SC/$BACKING_SIZE =="
helm --kube-context "$SVC_CTX" upgrade --install "$RELEASE" "$CHART" \
  --namespace "$NS" --create-namespace \
  --set storageClass.name="$SC" \
  --set storageClass.reclaimPolicy=Retain \
  --set storageClass.allowVolumeExpansion=true \
  --set persistence.enabled=true \
  --set persistence.storageClass="$BACKING_SC" \
  --set persistence.size="$BACKING_SIZE" \
  --wait --timeout 5m

echo "== 3) 검증 =="
echo "-- StorageClass $SC --"
kubectl --context "$SVC_CTX" get storageclass "$SC" \
  || { echo "FAIL: StorageClass $SC 없음 — helm values(storageClass.name) 확인"; exit 1; }
echo "-- provisioner 파드 --"
kubectl --context "$SVC_CTX" -n "$NS" get pods -l app=nfs-server-provisioner -o wide 2>/dev/null \
  || kubectl --context "$SVC_CTX" -n "$NS" get pods -o wide
READY="$(kubectl --context "$SVC_CTX" -n "$NS" get pods -o jsonpath='{.items[*].status.containerStatuses[*].ready}' 2>/dev/null || true)"
case "$READY" in
  *true*) echo "  ✅ provisioner Ready";;
  *)      echo "  ⚠️ provisioner 아직 Ready 아님 — 이미지 pull/기동 대기(노드 신뢰·mirror 확인). describe 로 점검.";;
esac

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ NFS 프로비저너 설치 완료(StorageClass=$SC, RWX)."
echo "   다음: prod overlay 의 components/file-storage-nfs 가 PVC(user-uploads/admin-uploads)를 만든다."
echo "        ArgoCD sync 후  kubectl --context $SVC_CTX -n si-msa get pvc  → Bound 확인."
echo "        파드 재기동(마운트 /mnt/uploads + FILE_STORAGE_TYPE=nas) 후 업로드가 NFS 에 영속."
