#!/usr/bin/env bash
# deploy/k8s/standalone-kind/11-host-access-verify.sh
# ─────────────────────────────────────────────────────────────────────────────
# B안 최종 PASS 게이트 — 호스트에서 port-forward 없이 harbor.local / jenkins.local 접속 확인.
#   세 좌표(호스트 hosts / 노드 /etc/hosts / CoreDNS)와 ingress 라우팅을 한 번에 점검한다.
#
# 전제: 10/08/09 완료 + 호스트 hosts 에 '127.0.0.1 harbor.local jenkins.local' 등록.
#   Windows: C:\Windows\System32\drivers\etc\hosts  (관리자 메모장)
#   WSL    : /etc/hosts                              (sudo)
#     127.0.0.1 harbor.local jenkins.local
#   ※ WSL2 면 보통 127.0.0.1 로 충분(Docker Desktop 이 localhost 포트를 WSL/Windows 양쪽에 게시).
# 실행: bash deploy/k8s/standalone-kind/11-host-access-verify.sh
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
FAIL=0

probe() { # name url expected_regex
  local name="$1" url="$2" want="$3"
  local code; code="$(curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || echo 000)"
  if echo "$code" | grep -qE "$want"; then
    echo "  ✅ $name : $url → HTTP $code"
  else
    echo "  ❌ $name : $url → HTTP $code (기대 $want)"; FAIL=1
  fi
}

echo "== A) hosts 등록 확인(클라이언트 측) =="
if getent hosts harbor.local >/dev/null 2>&1; then echo "  ✅ harbor.local 해소됨"; else echo "  ⚠️ harbor.local 미해소 — hosts 등록 필요"; fi
if getent hosts jenkins.local >/dev/null 2>&1; then echo "  ✅ jenkins.local 해소됨"; else echo "  ⚠️ jenkins.local 미해소 — hosts 등록 필요"; fi

echo "== B) ingress 진입(룰 없음 404 = 컨트롤러 정상) =="
probe "ingress-root" "http://localhost/" "404"

echo "== C) Harbor 포털/health(호스트→ingress→harbor-core) =="
probe "harbor-health" "http://harbor.local/api/v2.0/health" "200"
probe "harbor-portal" "http://harbor.local/" "200|30[0-9]"

echo "== D) Jenkins 로그인 페이지(호스트→ingress→jenkins) =="
probe "jenkins-login" "http://jenkins.local/login" "200|40[13]"

echo "== E) 인-클러스터 Kaniko 좌표(CoreDNS harbor.local→ingress) =="
# 임시 파드가 harbor.local 을 CoreDNS 로 해소해 health 200 받는지(=Kaniko push 경로 살아있는지).
INPOD="$(kubectl --context "$CTX" -n harbor run hc-$$ --rm -i --restart=Never --image=curlimages/curl:latest --quiet -- \
  -s -o /dev/null -w '%{http_code}' http://harbor.local/api/v2.0/health 2>/dev/null || echo 000)"
if echo "$INPOD" | grep -q 200; then echo "  ✅ 인-클러스터 harbor.local → HTTP $INPOD"; else echo "  ❌ 인-클러스터 harbor.local → HTTP $INPOD"; FAIL=1; fi

echo "== F) 노드 좌표 확인 =="
for n in $(docker ps --format '{{.Names}}' | grep "^${CLUSTER}-"); do
  H="$(docker exec "$n" getent hosts harbor.local 2>/dev/null | awk '{print $1}' | head -1)"
  T="$(docker exec "$n" cat /etc/containerd/certs.d/harbor.local/hosts.toml 2>/dev/null | grep -c 'http://harbor.local' || true)"
  echo "  $n: /etc/hosts harbor.local=${H:-없음} · certs.d(http://harbor.local)=${T}"
  [ -n "$H" ] && [ "$T" -ge 1 ] || FAIL=1
done

echo
echo "──────────────────────────────────────────────────────────────"
if [ "$FAIL" = "0" ]; then
  echo "✅ B안 PASS — 브라우저에서 http://harbor.local (admin/Harbor12345) · http://jenkins.local (admin/admin123)"
  echo "   port-forward 없이 접속. 다음: Jenkins UI 1회 잡 생성 → Build Now(파이프라인 그린)."
else
  echo "⚠️ 일부 실패 — 위 ❌ 항목 트리아지:"
  echo "  · harbor.local 미해소(A) → 호스트 hosts 등록."
  echo "  · ingress-root 404 아님(B) → 10-ingress-nginx / extraPortMappings(구 kind-config 가능)."
  echo "  · harbor-health 비200(C) → describe ingress -n harbor / ssl-redirect=false 확인 / externalURL."
  echo "  · 인-클러스터 비200(E) → CoreDNS harbor.local 매핑(08 §6) / coredns rollout."
  echo "  · 노드 좌표(F) → 07-reboot-recover.sh 재실행(CP_IP 재산출)."
fi
