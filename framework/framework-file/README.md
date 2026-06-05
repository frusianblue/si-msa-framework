# framework-file

파일 저장 공통(로컬/NAS 기본). 콘텐츠 타입 검증·확장자↔MIME 정합·at-rest AES 암호화·HTTP Range 스트리밍·안티바이러스 스캔을 옵트인으로 제공한다. S3 는 `framework-file-s3`, SFTP 는 `framework-file-sftp`.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-file') }   // core+mybatis 전제
```
```yaml
framework:
  file:
    enabled: true
    storage:
      type: local           # local(기본) | s3(framework-file-s3) | sftp(framework-file-sftp)
      base-path: ./uploads
      max-size: 10485760     # 10MB
      encrypt: false         # true 면 저장소에 AES-GCM 암호화 보관(at-rest)
    validation:
      content-type-detection: false   # true 면 Tika 로 실제 콘텐츠 타입 검출
      enforce-extension-match: false  # true 면 선언 확장자 ↔ 검출 MIME 계열 정합 강제
    scan:
      enabled: false         # ClamAV(INSTREAM) 스캔
```

## 쓰는 법
```java
private final FileService files;

FileMetaDto meta   = files.upload(multipartFile);     // 검증/스캔/암호화 후 저장 + 메타 영속
FileMetadata m     = files.getMeta(id);
InputStream in     = files.download(m);               // 한글 파일명은 RFC 5987
boolean range      = files.supportsRange();
InputStream chunk  = files.downloadRange(m, start, end);   // 부분 다운로드(스트리밍)
files.delete(id);                                     // ADMIN
```
S3 presigned 업로드는 `files.presignedStorageOrNull()` 후 `registerExternalUpload(...)` 로 메타 등록.

REST 는 `FileController`(multipart 업로드/다운로드/메타/삭제).


## 실전 사용 예 (코드)

업로드/다운로드는 스토리지 구현(local/S3/SFTP)에 무관하게 `FileService` 한 곳으로 처리한다. 내장 `FileController`(`/api/v1/files`)도 제공된다.
```java
// com.company.framework.file.service.FileService
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<FileMetaDto> upload(@RequestPart MultipartFile file) {
    return ApiResponse.ok(fileService.upload(file));   // 스캔/타입검증/(옵션)암호화 후 저장
}
```
```bash
# 업로드
curl -F 'file=@/path/photo.png' http://localhost:8080/api/v1/files
# 다운로드(Range 지원 스토리지면 부분 요청도 가능)
curl -H 'Range: bytes=0-1023' http://localhost:8080/api/v1/files/123 -o part.bin
```

## 끄는 법
`framework.file.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`FileStorage`/`FileScanner`/`FileContentTypeValidator` SPI 빈을 등록하면 기본(FileSystem/NoOp/Tika)을 교체(`@ConditionalOnMissingBean`).

## 버전 관리
Tika(`tikaVersion`)는 `compileOnly` 옵트인 — content-type-detection 쓸 때만 호스트에 추가. 신규 런타임 의존성 없음.
