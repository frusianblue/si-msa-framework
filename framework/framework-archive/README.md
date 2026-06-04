# framework-archive

아카이빙/압축 — ZIP(다중 엔트리)·GZIP(단일 스트림). 순수 JDK `java.util.zip` 기반 스트리밍, zip-slip 차단·압축폭탄 가드. 외부 의존성 0.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-archive') }   // framework-core 전제
```
```yaml
framework:
  archive:
    enabled: true                  # 기본 false
    max-entries: 10000             # 압축폭탄 가드 — 엔트리 수 상한
    max-entry-size: 104857600      # 엔트리당 100MB 상한
    max-total-bytes: 1073741824    # 총 1GB 상한
```

## 쓰는 법
```java
private final Archiver archiver;

archiver.zip(entries, out);                 // 다중 엔트리 → OutputStream (스트리밍)
archiver.unzip(in, entry -> { ... });       // 엔트리 단위 콜백(지연 스트림)
archiver.unzipToDirectory(in, targetDir);   // zip-slip 차단(ArchiveSafety)
archiver.gzip(in, out);                     // 단일 스트림 압축
archiver.gunzip(in, out);
```
한도 초과 시 `ArchiveErrorCode`(`ARC****`) 예외. `framework-file-batch` 의 압축 op 가 이 모듈에 위임.

## 끄는 법
`framework.archive.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`Archiver` SPI 를 구현하면 tar/tar.gz 등 다른 포맷을 추가할 수 있다(commons-compress 옵트인은 후속).

## 버전 관리
**신규 외부 의존성 0**(JDK `java.util.zip`).
