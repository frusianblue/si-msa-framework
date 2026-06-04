# 적용 안내 — QR 생성 + SFTP 후속(연결 풀·키 회전) + CI 게이트/jacoco 집계 (2026-06-04)

이 zip 은 저장소 **루트 기준 상대경로**로 묶여 있습니다. `si-msa-framework/` 루트에서 그대로 풀면
신규 파일 추가 + 기존 파일 덮어쓰기가 됩니다.

## 적용
    cd si-msa-framework
    unzip -o si-msa-2026-06-04-qr-sftp-ci.zip
    git add -A
    git status   # 변경 확인

## 받는 쪽 검증 (gradle 가능 환경)
    ./gradlew :framework:framework-qr:test
    ./gradlew :framework:framework-file-sftp:test
    ./gradlew :framework:framework-archtest:test
    ./gradlew testCodeCoverageReport
    ./gradlew :framework:framework-qr:spotlessApply :framework:framework-file-sftp:spotlessApply

## 포함 내용
- 신규 모듈 framework-qr (QrGenerator/ZxingQrGenerator/QrSpec/QrPngRenderer 등 + 테스트 3 + README)
- framework-file-sftp 후속: pool/ , cred/ , SftpKeyLoader + SftpFileStorage/AutoConfiguration 리팩터링 + 테스트 + README
- framework-file FileStorageProperties (sftp.pool / sftp.key-rotation nested 추가)
- 등록: settings.gradle / gradle/libs.versions.toml(zxing) / build.gradle(root: ext + jacocoAggregation) / framework-archtest build.gradle
- CI: deploy/cicd/ci-cd.yml , deploy/cicd/Jenkinsfile
- 문서: HANDOFF.md / HANDOFF_SUMMARY.md / README.md / STACK.md / docs/FRAMEWORK_MODULES.md

> ⚠️ 이 zip 은 변경/신규 파일만 포함합니다(전체 저장소 아님). 깨끗한 클론 위에 풀어 git diff 로 검토하세요.
