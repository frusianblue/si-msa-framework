#!/usr/bin/env bash
# deploy/k8s/prod-kind/00-up-clusters.sh
# ─────────────────────────────────────────────────────────────────────────────
# prod GitOps 리허설 1단계 — 인프라 토대 (1/4): kind 2클러스터 생성.
#   kind-cicd(hub: Harbor·Jenkins·ArgoCD) + kind-svc(서비스 4앱).
#   두 클러스터는 kind 기본 도커망 `kind` 를 공유 → 노드끼리 컨테이너 IP 로 도달(멀티클러스터 전제).
#
# 실행:  bash deploy/k8s/prod-kind/00-up-clusters.sh
# 멱등:  이미 있으면 생성 건너뜀.
# 다음:  01-data-containers.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

echo "== 0) 전제 =="
for b in docker kind kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 가 PATH 에 없음"; exit 1; }; done

create_cluster() {  # $1=name $2=config
  local name="$1" cfg="$2"
  if kind get clusters 2>/dev/null | grep -qx "$name"; then
    echo "  kind-$name 이미 존재 — 생성 건너뜀."
  else
    echo "  kind create cluster --name $name --config $cfg"
    kind create cluster --name "$name" --config "$cfg"
  fi
}

echo "== 1) kind-cicd(hub) 생성 (호스트 80/443) =="
create_cluster cicd kind-cicd-config.yaml

echo "== 2) kind-svc(서비스) 생성 (호스트 8080/8443) =="
create_cluster svc  kind-svc-config.yaml

echo '== 3) 두 클러스터가 kind 도커망을 공유하는지 확인 =='
NET_NAMES="$(docker network inspect kind -f '{{range .Containers}}{{.Name}} {{end}}' 2>/dev/null || true)"
echo "  kind 네트워크 컨테이너: $NET_NAMES"
for n in cicd-control-plane svc-control-plane; do
  echo "$NET_NAMES" | grep -qw "$n" || { echo "FAIL: $n 이 kind 네트워크에 없음 (멀티클러스터 도달 불가)"; exit 1; }
done

echo "== 4) 노드 Ready 확인 =="
for ctx in kind-cicd kind-svc; do
  echo "  --- $ctx ---"
  kubectl --context "$ctx" wait --for=condition=Ready nodes --all --timeout=120s
  kubectl --context "$ctx" get nodes -o wide
done

echo
echo "✅ 2클러스터 기동 완료 (kind-cicd 80/443 · kind-svc 8080/8443, 공유 kind 망)."
echo "   다음: bash 01-data-containers.sh"
