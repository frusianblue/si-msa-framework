# 적용 안내 (문서 업데이트 묶음)

저장소 루트에서:
```bash
unzip -o si-msa-docs-update.zip
```
하면 아래 문서가 제자리에 떨어진다(기존 덮어쓰기). 코드 변경 없음 — 전부 문서.

## 포함 (업데이트/신규)
```
HANDOFF_SUMMARY.md                       (전면 재작성: 이번 세션 + 다음 최우선=YAML 암호화)
HANDOFF.md                               (§6 함정 6건 추가 · §7 완료 항목 + 다음 우선순위 재정렬)
README.md                                (프로파일 규약 갱신: 기본=local·local-xx 오버레이·ENC 예정 안내)
STACK.md                                 (spotless 행 갱신 · 설정값 암호화 후보 행 추가)
BASELINE_FEATURES.md                     (완료 로그 + 다음 예정 추가)
docs/FRAMEWORK_MODULES.md                (환경/보안/spotless 완료 + crypto 다음 작업)
docs/BASELINE_FEATURES.md                (완료 로그 + 다음 예정 추가)
docs/NEXT_YAML_PASSWORD_ENCRYPTION.md    (★ 신규: 다음 세션 설계서 — 이게 핵심)
```

## 다음 세션 시작 방법
새 대화에 **HANDOFF_SUMMARY.md** 를 먼저 붙여넣고, 작업 들어가면 **docs/NEXT_YAML_PASSWORD_ENCRYPTION.md** 의
체크리스트(§7)대로 진행하면 된다. 핵심 결정은 이미 다 잡혀 있다: Jasypt 말고 커스텀 Boot4 EnvironmentPostProcessor + 기존 AesCryptoService.

> 참고: 이번 세션의 코드/설정 변경(프로파일 yml·audit 마이그레이션·JWT 가드·validation·build.gradle)은
> 이전에 받은 zip 들에 들어있다. 이 묶음은 그 작업을 반영한 **문서만** 담는다.
