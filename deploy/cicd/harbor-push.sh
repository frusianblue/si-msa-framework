#!/usr/bin/env bash
# deploy/cicd/harbor-push.sh
# 4서비스 양태그(:<git-sha> + 채널) 태깅/푸시 → 로컬 Harbor(ingress, HTTP).
#   사용:  ./deploy/cicd/harbor-push.sh                 # CHANNEL=dev 기본
#          CHANNEL=1.0.0 ./deploy/cicd/harbor-push.sh   # semver 채널
#   전제:  si-msa/<svc>:local 이미지가 이미 빌드돼 있어야 함
#          (없으면: docker compose -f deploy/compose/docker-compose.yml build)
#   ⚠️ 노출 경로: 이 환경은 NodePort 가 호스트/데몬 비노출 → Harbor 는 ingress(harbor.local) 경유.
#      자세히는 docs/ops/K8S_INGRESS_HARBOR.md. (구 NodePort localhost:30002 전제는 폐기.)
set -euo pipefail

REGISTRY="${REGISTRY:-harbor.local}"        # ingress 노출 주소(호스트 push=Windows hosts, 노드 pull=hosts.toml/자동노출)
PROJECT="${PROJECT:-si-msa}"
CHANNEL="${CHANNEL:-dev}"                    # 채널 태그(dev / semver). 불변태그(:<sha>)와 함께 발행
SRC_TAG="${SRC_TAG:-local}"                  # 로컬 빌드 산출 태그
HARBOR_USER="${HARBOR_USER:-admin}"
HARBOR_PASS="${HARBOR_PASS:-Harbor12345}"
SERVICES="gateway auth-server user-service admin-service"

SHA="$(git rev-parse --short HEAD)"
echo "==> registry=$REGISTRY project=$PROJECT git-sha=$SHA channel=$CHANNEL"

echo "==> docker login $REGISTRY"
echo "$HARBOR_PASS" | docker login "$REGISTRY" -u "$HARBOR_USER" --password-stdin

for s in $SERVICES; do
  src="si-msa/$s:$SRC_TAG"
  if ! docker image inspect "$src" >/dev/null 2>&1; then
    echo "!! 소스 이미지 없음: $src"
    echo "   먼저 빌드: docker compose -f deploy/compose/docker-compose.yml build $s"
    exit 1
  fi
  for t in "$SHA" "$CHANNEL"; do
    dst="$REGISTRY/$PROJECT/$s:$t"
    echo "==> tag+push $dst"
    docker tag "$src" "$dst"
    docker push "$dst"
  done
done

echo "==> done. 포털에서 확인:  http://$REGISTRY/harbor/projects  ($PROJECT → 4 repo × 2 tag)"
echo "==> overlay 이미지 핀(SHA):"
for s in $SERVICES; do
  echo "    kustomize edit set image registry.example.com/si-msa/$s=$REGISTRY/$PROJECT/$s:$SHA"
done
