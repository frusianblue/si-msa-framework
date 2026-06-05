# framework-qr — QR 코드 생성(ZXing 옵트인)

문자열/URI 를 **QR PNG** 로 인코딩하는 선택형 모듈. 선택형 모듈 컨벤션대로 **기본 비활성**.

전형적 용도: MFA `otpauth://` URI(인증기 앱 등록용), 결제/전자문서 검증 URL, 입장권/쿠폰 등.

> **MFA 모듈과의 관계**: `framework-mfa` 는 의도적으로 zxing 의존을 피해 **`otpauth://` URI 만** 돌려주고
> QR 그리기는 클라이언트에 맡긴다. **서버측 QR PNG** 가 필요한 프로젝트만 이 모듈을 옵트인하면 된다.

## 의존성

인코딩 엔진은 **ZXing core** 가 필수다(QR 의 Reed-Solomon 오류정정·마스킹은 직접 구현 대상이 아님).
단 렌더링(BitMatrix → PNG)은 ZXing `javase`(`MatrixToImageWriter`) 대신 **JDK 내장 ImageIO** 로 직접 처리해
의존성을 **`zxing-core` 1개**로 최소화했다(`framework-image` 가 ImageIO 만 쓰는 것과 같은 결).

- `com.google.zxing:core` — BOM 밖 → `libs.versions.toml(zxing)` + 루트 ext(`zxingVersion`) 로 고정(3.5.4).
- 모듈 내부 구현 디테일이므로 `implementation`(전이 금지) — 소비 서비스는 프레임워크 타입(`QrGenerator`)만 의존.
- `zxing-core` 부재 시 `@ConditionalOnClass(QRCodeWriter)` 로 오토컨피그가 통째로 백오프.

## 구성요소

| 클래스 | 역할 |
|--------|------|
| `QrGenerator` | 생성 SPI(교체 가능). `toPng(content)` / `generate(content, spec)`. 앱이 빈 재정의 시 우선. |
| `ZxingQrGenerator` | 기본 구현. ZXing 인코딩 → ImageIO PNG 렌더링. 실패는 `BusinessException(QrErrorCode)`. |
| `QrSpec` | 렌더링 명세(불변·빌더). 크기/여백/ECC/문자셋/색. **출력은 항상 PNG(무손실)** — 손실 포맷은 스캔 실패 유발. |
| `QrEccLevel` | 오류정정 레벨(L/M/Q/H) — ZXing 무의존 enum(매핑은 구현 내부). |
| `QrPngRenderer` | 격자(`PixelGrid`) → PNG. **ZXing 무의존** → JDK 단독 단위검증 경계. |
| `QrErrorCode` | 표준 `BusinessException` 용 에러코드(`QR****`). |

## 켜는 법

```yaml
framework:
  qr:
    enabled: true              # 기본 false (끄면 빈 미등록 → 무비용)
    default-size-px: 256       # toPng(content) 기본 한 변(px)
    default-margin: 4          # 기본 조용한 영역(모듈 수, 권장 4 이상)
    default-ecc-level: M       # 기본 오류정정(L/M/Q/H)
    default-charset: UTF-8     # 기본 바이트 인코딩 문자셋(한글/이모지 URL 안전)
    max-content-length: 1024   # 인코딩 전 차단할 내용 길이 상한(문자 수, 0 이하면 검사 생략)
```

빌드에 의존 추가(소비 서비스):

```gradle
implementation project(':framework:framework-qr')
// zxing-core 는 framework-qr 가 implementation 으로 가지므로 별도 선언 불필요.
```

## 사용

```java
@Service
class MfaQrService {
    private final QrGenerator qr;            // 생성자 주입
    private final TotpSecretGenerator totp;  // framework-mfa

    public byte[] enrollmentQr(String account, String secretBase32) {
        String uri = totp.provisioningUri("si-msa", account, secretBase32); // otpauth://...
        return qr.toPng(uri);                 // 기본 스펙 PNG
    }

    public byte[] verifyLinkQr(String url) {
        return qr.generate(url, QrSpec.builder()
                .sizePx(512)
                .eccLevel(QrEccLevel.Q)       // 로고 오버레이/인쇄 대비 여유
                .margin(4)
                .build());
    }
}
```

컨트롤러에서 이미지로 내려줄 때:

```java
@GetMapping(value = "/mfa/qr", produces = MediaType.IMAGE_PNG_VALUE)
public ResponseEntity<byte[]> qr(...) {
    byte[] png = mfaQrService.enrollmentQr(account, secret);
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
}
```


## 실전 사용 예 (코드)

`QrGenerator` 로 QR PNG 바이트를 생성한다(ZXing, 옵트인). 보통 컨트롤러에서 이미지로 내려준다.
```java
// com.company.framework.qr.{QrGenerator, QrSpec, QrEccLevel}
private final QrGenerator qr;

@GetMapping(value = "/api/v1/qr", produces = MediaType.IMAGE_PNG_VALUE)
public byte[] qr(@RequestParam String text) {
    return qr.toPng(text);   // 기본 스펙 PNG
}
// 크기/오류정정 지정
byte[] big = qr.generate("https://corp.com/pay/123", new QrSpec(512, QrEccLevel.H));
```
```bash
curl 'http://localhost:8080/api/v1/qr?text=hello' -o qr.png
```

## 끄는 법
```yaml
framework.qr.enabled: false   # 기본값(opt-in) — 명시하지 않으면 비활성
```
끄면 `QrGenerator` 빈이 등록되지 않는다. mfa 는 이 빈이 없어도 동작하며(otpauth:// URI 만 제공), QR PNG 보강이 필요할 때만 켠다.

## 설계 메모

- **PNG 전용(JPEG 미지원)**: QR 은 흑백 경계가 날카로워야 인식된다. JPEG 같은 손실 압축은 경계에 잡음을 남겨
  스캔 실패를 유발하므로 의도적으로 출력 포맷을 PNG 로 고정한다.
- **렌더링 seam(`PixelGrid`)**: ZXing `BitMatrix` 를 직접 노출하지 않고 `PixelGrid` 로 어댑트 → `QrPngRenderer`
  가 ZXing 무의존이 되어 JDK 단독 하니스로 실코드 그대로 검증 가능.
- **실패 매핑**: 빈 내용 `EMPTY_CONTENT` · 상한 초과 `CONTENT_TOO_LONG` · ZXing 인코딩 실패(용량 초과 등)
  `ENCODE_FAILED` · PNG 쓰기 실패 `RENDER_FAILED`. 내부 메시지/스택은 응답에 싣지 않는다.

## 받는 쪽 확인

```bash
./gradlew :framework:framework-qr:test          # 왕복(인코딩→디코딩)·검증·오토컨피그
./gradlew :framework:framework-qr:spotlessApply # Palantir 포맷
```
