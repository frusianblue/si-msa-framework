# 적용 방법 (문서 정리 1단계)

레포 **루트**에서 (fresh clone 위에서) 2단계만 실행한다.

```bash
# 1) 이 zip 을 레포 루트에 풀면 신규/슬림 문서가 제 위치에 놓인다
#    (docs/00_INDEX.md, docs/guide/*, README.md, reorg-docs.sh)
unzip -o docs-reorg.zip

# 2) 재배치 스크립트 실행 (작업기록→_internal, 레퍼런스→reference, 운영→ops + 링크 보정)
bash reorg-docs.sh

# 확인
find docs -maxdepth 2 -type f | sort
git status
```

`git mv` 로 이동하므로 이력이 보존된다. 결과가 마음에 들면 commit, 아니면 `git checkout .` 로 되돌린다.
적용 후 `reorg-docs.sh` 와 `APPLY.md` 는 삭제해도 된다.
