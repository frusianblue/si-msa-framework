#!/usr/bin/env bash
# =============================================================================
# reorg-docs.sh — si-msa-framework 문서 재배치 (1단계)
#   - 작업/세션 기록 → docs/_internal/
#   - 레퍼런스 → docs/reference/ , 운영/배포 → docs/ops/
#   - 기능 문서는 docs/modules/ 유지, FRAMEWORK_MODULES.md 는 docs/ 유지
#   - 신규: docs/00_INDEX.md(진입점) · docs/guide/MODULE_COMPOSITION.md(조합 매트릭스)
#   - README.md 슬림화
# 사용: 레포 루트에서  bash reorg-docs.sh
# 안전: git 저장소면 git mv(이력 보존), 아니면 mv 로 폴백. 이미 옮긴 항목은 건너뜀.
# =============================================================================
set -euo pipefail

[ -f settings.gradle ] && [ -d framework ] || { echo "✗ 레포 루트에서 실행하세요(settings.gradle/framework 없음)"; exit 1; }

USE_GIT=0
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then USE_GIT=1; fi

mv_one() { # $1=src $2=dstdir
  local src="$1" dstdir="$2"
  [ -e "$src" ] || { echo "  · skip(없음): $src"; return 0; }
  mkdir -p "$dstdir"
  if [ "$USE_GIT" -eq 1 ]; then git mv -k "$src" "$dstdir/" 2>/dev/null || mv "$src" "$dstdir/"; else mv "$src" "$dstdir/"; fi
  echo "  → $src  ⇒  $dstdir/"
}

echo "[1/6] 디렉터리 생성"
mkdir -p docs/reference docs/ops docs/guide docs/_internal/apply-notes docs/_internal/planning docs/_internal/archive

echo "[2/6] 작업/세션 기록 → docs/_internal/"
for f in HANDOFF.md HANDOFF_SUMMARY.md HANDOFF_SUMMARY_TEMPLATE.md; do mv_one "$f" docs/_internal; done
for f in APPLY_NOTES.md APPLY_NOTES_LOCAL.md APPLY_THIS_SESSION.md; do mv_one "$f" docs/_internal/apply-notes; done
mv_one docs/SPOTLESS_NOTES.md docs/_internal
for f in docs/NEXT_OIDC_ID_TOKEN.md docs/NEXT_RP_IDTOKEN_LINK.md docs/NEXT_SIGNING_KEY_ROTATION.md docs/NEXT_SSO.md; do mv_one "$f" docs/_internal/planning; done
for f in docs/archive/NEXT_FILE_BATCH_PROCESSING.md docs/archive/NEXT_YAML_PASSWORD_ENCRYPTION.md; do mv_one "$f" docs/_internal/archive; done
rmdir docs/archive 2>/dev/null || true

echo "[3/6] 레퍼런스 → docs/reference/"
mv_one STACK.md docs/reference
for f in docs/BASELINE_FEATURES.md docs/CHANGES_AND_DEPRECATIONS.md docs/ENCRYPTION_GUIDE.md docs/TOKEN_VERIFICATION_GUIDE.md docs/SECURITY_VALIDATION_ADDITIONS.md; do mv_one "$f" docs/reference; done

echo "[4/6] 운영/배포 → docs/ops/"
mv_one docs/LOCAL_SETUP.md docs/ops
for f in docs/modules/DEV_ENV_WINDOWS.md docs/modules/LOCAL_K8S_ENV_SETUP.md docs/modules/LOCAL_K8S_TEST.md docs/modules/K8S_ADDONS.md docs/modules/K8S_CICD_MULTISERVICE.md; do mv_one "$f" docs/ops; done

echo "[5/6] 이동으로 깨진 상대 링크 보정"
sed_i() { [ -f "$1" ] && sed -i "$2" "$1" && echo "  · $1"; }

# (a) reference/TOKEN_VERIFICATION_GUIDE: docs/ → docs/reference/ (한 단계 깊어짐) ./modules → ../modules
sed_i docs/reference/TOKEN_VERIFICATION_GUIDE.md 's#](\./modules/#](../modules/#g'

# (b) modules/AUTH_SERVER · OIDC_HARDENING (제자리, ../ 기준): 이동된 reference/NEXT 가리키게
for f in docs/modules/AUTH_SERVER.md docs/modules/OIDC_HARDENING.md; do
  sed_i "$f" 's#](\.\./TOKEN_VERIFICATION_GUIDE.md#](../reference/TOKEN_VERIFICATION_GUIDE.md#g; s#](\.\./ENCRYPTION_GUIDE.md#](../reference/ENCRYPTION_GUIDE.md#g; s#](\.\./NEXT_#](../_internal/planning/NEXT_#g'
done

# (c) _internal/planning/NEXT_*: docs/ → docs/_internal/planning/ (두 단계) ./modules → ../../modules
for f in docs/_internal/planning/NEXT_*.md; do
  sed_i "$f" 's#](\./modules/#](../../modules/#g'
done

# (d) services/*/README: ../../docs/<file> → ../../docs/<newdir>/<file>
for f in services/*/README.md; do
  sed_i "$f" 's#](\.\./\.\./docs/ENCRYPTION_GUIDE.md#](../../docs/reference/ENCRYPTION_GUIDE.md#g; s#](\.\./\.\./docs/TOKEN_VERIFICATION_GUIDE.md#](../../docs/reference/TOKEN_VERIFICATION_GUIDE.md#g; s#](\.\./\.\./docs/LOCAL_SETUP.md#](../../docs/ops/LOCAL_SETUP.md#g; s#](\.\./\.\./docs/NEXT_#](../../docs/_internal/planning/NEXT_#g'
done

echo "[6/6] 신규/재작성 문서 기록"
# (00_INDEX.md, guide/MODULE_COMPOSITION.md, 슬림 README.md 는 동봉본을 덮어씀 — 아래 PLACEHOLDER 참고)
echo "  · 신규 문서는 zip 동봉본으로 배치됨(00_INDEX / MODULE_COMPOSITION / README)"

echo ""
echo "✓ 재배치 완료. 확인:  find docs -maxdepth 2 -type f | sort"
echo "  새 진입점:  docs/00_INDEX.md"
