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

echo "== 0.5) 호스트 포트 선점 검사 (cicd 80/443 · svc 8080/8443) =="
# extraPortMappings 는 생성 시 고정 → 점유된 포트가 있으면 kind create 가 중간에 bind 실패(메시지 지저분).
# 여기서 먼저 잡아 명확히 실패시킨다. 가장 흔한 범인 = 기존 kind-sanity(standalone-kind B안, 호스트 80/443 게시).
port_holder() {  # $1=port → 점유 컨테이너명(없으면 빈 문자열)
  docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null \
    | grep -E "(0\.0\.0\.0|:::)?:$1->" | awk '{print $1}' | head -1
}
PORTBAD=0
for p in 80 443 8080 8443; do
  h="$(port_holder "$p")"
  if [ -n "$h" ]; then
    # 이미 cicd/svc 노드가 잡고 있으면(=재실행) 무해 — 그 외 컨테이너면 충돌.
    case "$h" in
      cicd-*|svc-*) : ;;
      *) echo "  [충돌] 호스트 포트 $p → 컨테이너 '$h' 가 점유 중"; PORTBAD=1 ;;
    esac
  fi
done
if [ "$PORTBAD" = 1 ]; then
  echo
  echo "FAIL: 호스트 포트가 점유돼 있어 클러스터를 만들 수 없습니다."
  echo "  대개 기존 kind-sanity(standalone-kind, 호스트 80/443 게시)가 원인입니다. 해결 택1:"
  echo "    A) sanity 미사용 → kind delete cluster --name sanity   (80/443 해제 후 재실행)"
  echo "    B) sanity 유지   → kind-cicd-config.yaml 의 hostPort 80/443 을 8000/8043 등으로 변경 + hosts 도 그 포트로"
  echo "  확인:  docker ps --format '{{.Names}}\\t{{.Ports}}' | grep -E '0.0.0.0:80|:443'"
  exit 1
fi
echo "  80/443/8080/8443 사용 가능."

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
