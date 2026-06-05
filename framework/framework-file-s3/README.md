# framework-file-s3

`framework-file` 의 **S3 저장 백엔드**. `storage.type=s3` 일 때 활성. AWS SDK v2 기반, presigned PUT/GET 지원.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-file-s3') }   // framework-file 전이
```
```yaml
framework:
  file:
    enabled: true
    storage:
      type: s3
      encrypt: false
      presigned-put-ttl: 10m
      presigned-get-ttl: 5m
      s3:
        bucket: ${S3_BUCKET}
        region: ap-northeast-2
        endpoint: ${S3_ENDPOINT}   # MinIO 등 호환 스토리지 시 지정(없으면 AWS 기본)
```
자격증명은 표준 AWS 체인(환경변수/IAM 역할 등).

## 쓰는 법
`framework-file` 의 `FileService` 를 그대로 쓴다 — `storage.type=s3` 면 내부 `S3FileStorage` 로 위임. 대용량은 presigned PUT 으로 클라이언트 직접 업로드 후 `registerExternalUpload(...)` 로 메타 등록.


## 실전 사용 예 (코드)

S3 백엔드를 켜면 `FileService` 가 그대로 S3 를 쓰며, 추가로 **presigned URL** 직접 업/다운로드를 지원한다(서버 트래픽 우회).
```java
// FileService.presignedStorageOrNull() → PresignedUrlStorage
PresignedUrlStorage ps = fileService.presignedStorageOrNull();
if (ps != null) {
    PresignedUrl put = ps.presignPut("photo.png", "image/png", Duration.ofMinutes(5));
    // put.url() 을 클라이언트에 내려주면 클라이언트가 S3 로 직접 PUT
}
```
```bash
# 내장 엔드포인트: presigned 발급 → 클라이언트가 직접 업로드
curl -X POST 'http://localhost:8080/api/v1/files/presigned-upload?name=photo.png&contentType=image/png'
curl http://localhost:8080/api/v1/files/123/presigned     # 다운로드용 GET presigned
```

## 끄는 법
`storage.type` 을 `local`/`sftp` 로 두거나 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`FileStorage` 빈을 직접 등록하면 양보(`@ConditionalOnMissingBean`).

## 버전 관리
AWS SDK v2 BOM(`awsSdkVersion`)으로 `s3` 모듈 버전 정렬. 변경 시 `STACK.md` 갱신.
