# framework-file-batch

여러 파일에 **동일 작업을 한꺼번에**(이름변경/이미지변환/압축) 적용한다. 부분실패 격리·결과 수집·Java 21 가상스레드 병렬·드라이런을 제공. 변환/압축은 `framework-image`/`framework-archive` 에 위임한다.

## 켜는 법
```gradle
dependencies {
  implementation project(':framework:framework-file-batch')
  // 변환/압축 op 를 쓰려면 함께(없으면 해당 op 만 빠지고 rename/오케스트레이터는 동작)
  implementation project(':framework:framework-image')
  implementation project(':framework:framework-archive')
}
```
```yaml
framework:
  file-batch:
    enabled: true          # 기본 false
    default-parallelism: 16
```

## 쓰는 법
```java
private final FileBatchProcessor processor;

BatchResult result = processor.process(
    items,                          // List<BatchItem> (입력 순서 보존)
    new RenameOperation(...),       // prefix/suffix/regex/sequence/template, 충돌 FAIL|SUFFIX
    BatchOptions.builder().dryRun(false).parallelism(8).build());

result.outcomes();   // 항목별 성공/실패 (부분실패 격리)
```
`ImageTransformOperation`(→ framework-image), `CompressOperation`(→ framework-archive 파일별 gzip)도 동일 `BatchFileOperation` SPI. 이름 충돌 사전검출은 `BatchPreflight`.

> image/archive 는 `compileOnly`+`@ConditionalOnClass`/`@ConditionalOnBean` 백오프 — 없으면 그 op 만 비활성.

## 끄는 법
`framework.file-batch.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`BatchFileOperation` 을 구현해 새 일괄 작업을 추가할 수 있다.

## 버전 관리
순수 로직(Spring 무의존 핵심) + 위임 모듈만 사용 — **신규 외부 의존성 0**.
