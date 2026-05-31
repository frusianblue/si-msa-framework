# INSTALL — 문서 갱신 드롭인 (2026-05-31)

`si-msa-framework/` 루트에서 `unzip -o` 하면 문서가 갱신/추가된다(코드 변경 없음).

| 파일 | 상태 |
|---|---|
| `HANDOFF_SUMMARY.md` | **신규** — 세션 한 장 요약(다음 채팅에서 바로 사용) |
| `HANDOFF.md` | 갱신 — 모듈 목록·함정·현재상태·문서지도 |
| `README.md` | 갱신 — 모듈 선택 목록 + 토대 4종 사용법 섹션 |
| `STACK.md` | 갱신 — 신규 모듈 의존성 메모 + Boot4/Spring7 호환 주의 |
| `docs/FRAMEWORK_MODULES.md` | 갱신/배치 — 진행현황(✅ 토대 4종) |

```bash
unzip -o framework-docs-update.zip -d /path/to/si-msa-framework
git add -A && git status   # 변경 확인
```
