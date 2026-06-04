# 적용 안내 — QR 생성 + SFTP 후속 + CI 게이트/jacoco 집계 (2026-06-04, final)

> final: 받는 쪽 gradle 전 항목 통과 확인 완료(:framework-qr:test / :framework-file-sftp:test /
> :framework-archtest:test / testCodeCoverageReport / spotless). rev2 대비 인계 문서 상태 줄만 갱신.

이 zip 은 저장소 **루트 기준 상대경로**로 묶여 있습니다. si-msa-framework/ 루트에서 그대로 풀면
신규 파일 추가 + 기존 파일 덮어쓰기가 됩니다.

## 적용
    cd si-msa-framework
    unzip -o si-msa-2026-06-04-qr-sftp-ci-final.zip
    git add -A && git status
    git commit -m "framework-qr 신설 + framework-file-sftp 연결 풀/키 회전 + CI 게이트/멀티모듈 jacoco 집계"

## 검증 (전부 통과 확인됨, 2026-06-04)
    ./gradlew :framework:framework-qr:test                 # ✅
    ./gradlew :framework:framework-file-sftp:test          # ✅
    ./gradlew :framework:framework-archtest:test           # ✅
    ./gradlew testCodeCoverageReport                       # ✅ (루트 BOM import 후)
    ./gradlew :framework:framework-qr:spotlessApply :framework:framework-file-sftp:spotlessApply  # ✅

## 포함 내용
- 신규 모듈 framework-qr (+테스트 3 +README)
- framework-file-sftp 후속: pool/ , cred/ , SftpKeyLoader + 리팩터링 + 테스트 + README
- framework-file FileStorageProperties (sftp.pool / sftp.key-rotation)
- 등록: settings.gradle / gradle/libs.versions.toml / build.gradle(root: ext + 루트 BOM import + jacocoAggregation) / framework-archtest
- CI: deploy/cicd/ci-cd.yml , Jenkinsfile
- 문서: HANDOFF.md / HANDOFF_SUMMARY.md / README.md / STACK.md / docs/FRAMEWORK_MODULES.md
