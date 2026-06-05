# framework-image — 이미지 처리(리사이즈 / 썸네일 / EXIF)

업로드 이미지를 **비율 유지 축소·썸네일**로 만들고, **EXIF orientation 을 실제 픽셀에 보정**하며,
**민감 메타데이터(GPS 포함)를 제거**하는 선택형 모듈. 선택형 모듈 컨벤션대로 **기본 비활성**.

**새 외부 의존성 0** — 엔진은 JDK 내장 `javax.imageio` + `java.awt` 만 사용한다. EXIF orientation 은
메타데이터 라이브러리 없이 JPEG APP1/TIFF 를 직접 파싱하고(`ExifOrientation`), 메타 제거는 디코드→리인코딩 시
메타가 보존되지 않는 성질로 자동 처리한다. 웹 비의존 — 배치/스케줄 등 비웹 컨텍스트에서도 사용 가능.

## 구성요소

| 클래스 | 역할 |
|--------|------|
| `ImageProcessor` | 처리 SPI(교체 가능). 앱이 빈 재정의 시 우선. |
| `DefaultImageProcessor` | ImageIO 기반 기본 구현. 폭탄 방지 → orientation 보정 → 비율유지 축소 → 리인코딩(메타 제거). |
| `ExifOrientation` | JPEG EXIF orientation(0x0112) 만 직접 읽는 순수 파서(실패 시 NORMAL, 예외 없음). |
| `ResizeSpec` | 리사이즈/인코딩 명세(불변·빌더). 박스 상한·업스케일·포맷·품질·보정여부. |
| `ImageFormat` | 출력 포맷 화이트리스트(JPEG/PNG). |
| `ImageInfo` | 디코드 없이 헤더로 읽은 크기/포맷. |
| `ImageErrorCode` | 표준 `BusinessException` 용 에러코드(`IMG****`). |

## 켜는 법

```yaml
framework:
  image:
    enabled: true               # 기본 false (끄면 빈 미등록 → 무비용)
    default-format: JPEG        # 썸네일 기본 출력 포맷(JPEG/PNG)
    thumbnail-max-edge: 320     # thumbnail(maxEdge) 기본 변 길이
    jpeg-quality: 0.85          # JPEG 품질 0.0~1.0
    max-source-pixels: 40000000 # 디코드 허용 최대 픽셀(디컴프레션 폭탄 방지)
```

## 사용

```java
@Service
class AvatarService {
    private final ImageProcessor images;       // 생성자 주입

    public byte[] makeAvatar(byte[] uploaded) {
        // EXIF 보정 + 256 박스 비율유지 축소 + JPEG 리인코딩(메타 제거)
        return images.process(uploaded, ResizeSpec.builder()
                .maxEdge(256)
                .format(ImageFormat.JPEG)
                .quality(0.85f)
                .correctOrientation(true)
                .build());
    }

    public byte[] thumb(byte[] uploaded) {
        return images.thumbnail(uploaded, 128);  // 정사각 박스 편의 메서드
    }

    public ImageInfo inspect(byte[] uploaded) {
        return images.probe(uploaded);            // 디코드 없이 크기/포맷만
    }
}
```


## 실전 사용 예 (코드)

`ImageProcessor` 로 리사이즈/썸네일/메타조회를 한다(EXIF 회전 보정 포함).
```java
// com.company.framework.image.{ImageProcessor, ResizeSpec, ImageInfo, ImageFormat}
private final ImageProcessor images;

public byte[] makeThumb(byte[] original) {
    return images.thumbnail(original, 200);   // 긴 변 200px 썸네일
}
public byte[] webOptimized(byte[] original) {
    ResizeSpec spec = new ResizeSpec(1280, 1280, false, ImageFormat.JPEG, 0.8f, true);
    return images.process(original, spec);    // 업스케일 금지 + 품질 0.8 + 방향보정
}
public void inspect(byte[] original) {
    ImageInfo info = images.probe(original);  // info.width(), info.height(), info.formatName()
}
```

## 주의 / 함정

- **메타 제거는 리인코딩의 부수효과**: ImageIO 디코드→재인코딩 시 EXIF/GPS 등이 보존되지 않으므로 별도 strip
  단계가 없다. 즉 `process()` 출력은 항상 메타가 없다(원본을 그대로 보관하려면 별도 보존 경로 필요).
- **EXIF 보정은 best-effort**: `ExifOrientation` 은 JPEG APP1 이 없거나 파싱이 어긋나면 예외 없이 NORMAL 을
  반환한다 — "있으면 적용, 없으면 원본 유지". PNG/기타 포맷은 orientation 개념이 없어 보정 대상이 아니다.
- **디컴프레션 폭탄 방지**: 디코드 전에 헤더로 픽셀 수를 검사해 `max-source-pixels` 초과 시 거부한다
  (`IMAGE_TOO_LARGE`). 업로드 바이트 크기 제한과 별개의 방어선.
- **JPEG 알파 평탄화**: JPEG 는 알파를 못 담으므로 출력이 JPEG 면 흰 배경으로 평탄화한다. 투명도 보존이 필요하면
  `ImageFormat.PNG` 로 출력.
- **헤드리스**: AWT 오프스크린 렌더만 쓰므로 서버/컨테이너(헤드리스)에서 동작한다(디스플레이 불필요).
- `framework.image.enabled=false`(기본)면 빈이 전혀 등록되지 않아 무비용.
