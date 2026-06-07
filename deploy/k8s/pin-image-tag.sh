#!/usr/bin/env bash
# deploy/k8s/pin-image-tag.sh
# ───────────────────────────────────────────────────────────────────────────────
# overlay 의 이미지 sentinel(`__GITSHA__`)을 **불변 태그**로 치환한다(단일 주입 지점).
#   B 결정(2026-06-07): 가변 `:dev` 핀 + `kubectl set image :<sha>` 명령형 덮어쓰기 폐기.
#   무엇이 뜨는지의 단일 진실 = 불변 git-sha 태그. 이 스크립트가 그 태그를 overlay 에 declarative 로 박는다.
#
#   ★ 항상 **체크아웃 워크스페이스(ephemeral)** 의 kustomization.yaml 만 고친다 — git 되커밋 없음.
#     CI 는 잡 워크스페이스, 수동은 임시 복사본 dir 에 대해 호출하라.
#
# 사용:
#   bash deploy/k8s/pin-image-tag.sh <overlay-dir> <tag>
#   예) bash deploy/k8s/pin-image-tag.sh deploy/k8s/overlays/dev a1b2c3d4e5f6
#
# 멱등: sentinel 이 이미 치환됐으면(=__GITSHA__ 부재) no-op 으로 통과(재실행 안전).
# 의존: sh + sed (alpine/kubectl·busybox 포함). kustomize 바이너리 불요.
# ───────────────────────────────────────────────────────────────────────────────
set -eu

DIR="${1:?사용: pin-image-tag.sh <overlay-dir> <tag>}"
TAG="${2:?사용: pin-image-tag.sh <overlay-dir> <tag>}"
KFILE="$DIR/kustomization.yaml"

[ -f "$KFILE" ] || { echo "FAIL: $KFILE 없음"; exit 1; }

# 태그 형식 방어(docker 태그: 영숫자/_/-/. 1~128자). 빈 sha·공백 주입 사고 차단.
case "$TAG" in
  *[!A-Za-z0-9_.-]*|"") echo "FAIL: 유효하지 않은 태그 '$TAG'"; exit 1;;
esac

if grep -q '__GITSHA__' "$KFILE"; then
  # sed -i 호환(GNU/BusyBox 모두): 임시파일 경유.
  sed "s/__GITSHA__/${TAG}/g" "$KFILE" > "$KFILE.tmp" && mv "$KFILE.tmp" "$KFILE"
  echo "  pinned: $KFILE 의 모든 이미지 → :$TAG"
else
  echo "  (sentinel __GITSHA__ 없음 — 이미 치환됐거나 대상 아님; no-op)"
fi
