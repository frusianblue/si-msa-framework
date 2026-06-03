# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**기본기능 카탈로그 #7 `framework-image` 신설.** 업로드 이미지를 **비율유지 리사이즈/썸네일**로 가공하고, **EXIF orientation 을 픽셀에 보정**하며, **민감 EXIF(GPS 포함) 를 제거**(디코드→리인코딩 부수효과)한다. 엔진은 JDK 내장 `javax.imageio`+`java.awt` 만 — **신규 외부 의존성 0**. 출력 화이트리스트 JPEG/PNG, 디컴프레션 폭탄 방지(디코드 전 헤더 픽셀수 검사), JPEG 알파 흰배경 평탄화, 헤드리스·웹 비의존(배치/MQ 컨슈머 사용). 3단 토글·기본 off. 분산락·PDF·캐시·로그마스킹·요청컨텍스트에 이어 이미지 가공까지.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### A. framework-image (이미지 처리, 카탈로그 #7)
1. **SPI/모델(순수 JDK)**: `ImageProcessor`(`process(bytes, ResizeSpec)`/`thumbnail(bytes, maxEdge)`/`probe(bytes)`) · `ResizeSpec`(record·검증 compact 생성자 + `builder()`/`thumbnail()` 팩토리, `maxEdge()` 동시설정) · `ImageFormat`(JPEG/PNG 화이트리스트, 느슨한 `fromName`) · `ImageInfo`(width/height/formatName) · `ImageErrorCode`(IMG**** implements core `ErrorCode`).
2. **EXIF 파서(`ExifOrientation`, 라이브러리 0)**: JPEG SOI→마커 순회→APP1("Exif\0\0")→TIFF 헤더(II/MM 엔디안·magic 0x2A)→IFD0→태그 0x0112(SHORT) 직접 파싱. 전구간 경계검사, 실패/부재/널/비JPEG/잘림 시 예외 없이 `NORMAL`(1). `swapsDimensions(5~8)=true`.
3. **엔진(`DefaultImageProcessor`)**: 헤더로 픽셀수 폭탄검사(초과→IMAGE_TOO_LARGE)→디코드→`applyOrientation`(8케이스 AffineTransform, 5~8 가로세로 스왑)→`resizeWithinBox`(비율유지, 업스케일 옵트인, 2배 초과는 단계적 절반축소 highQualityScale)→`encode`(JPEG=알파 흰배경 평탄화+`ImageWriteParam` 품질, PNG=`ImageIO.write`; 메타 미기록=자동 제거). `probe` 는 `ImageReader` 로 디코드 없이 크기/포맷.
4. **오토컨피그/프로퍼티**: `ImageAutoConfiguration`(`@AutoConfiguration`+`@ConditionalOnProperty(framework.image enabled=true)`+`@EnableConfigurationProperties`, `@Bean @ConditionalOnMissingBean ImageProcessor`). **웹 비의존**(`@ConditionalOnWebApplication` 미부착 — 배치/MQ 사용). `ImageProperties`(enabled=false·default-format=JPEG·thumbnail-max-edge=320·jpeg-quality=0.85·max-source-pixels=40,000,000, Lombok 미사용 plain getter/setter).
5. **테스트 5종**: `ExifOrientationTest`(orient 1~8 합성 APP1·비JPEG/잘림/널/범위밖→NORMAL·swapsDimensions) · `ImageFormatTest` · `ResizeSpecTest`(빌더 기본값/thumbnail/maxEdge/검증 거부) · `DefaultImageProcessorTest`(800x400→200박스→200x100·업스케일 금지/허용·orient6 보정→가로세로 스왑+메타 제거 후 NORMAL·보정 off·알파 JPEG 평탄화·PNG 알파 보존·thumbnail·probe·폭탄→IMAGE_TOO_LARGE·빈→EMPTY_IMAGE·garbage→DECODE_FAILED) · `config/ImageAutoConfigurationTest`(비웹 `ApplicationContextRunner`: disabled 기본/enabled 등록/프로퍼티 바인딩/앱빈 우선 isSameAs/`.imports` 가드).

### B. 등록/배선 + 문서
6. **등록/배선**: `settings.gradle` include(context 다음, archtest 앞) · `.imports`(`ImageAutoConfiguration`) · `framework-archtest/build.gradle` 에 `testImplementation project(':framework:framework-image')`(arch 스캔). 신규 슬라이스 `image`→core 단방향(순환 없음).
7. **문서 동기화**: 모듈 `README.md` 신설 · 루트 `README.md`(요약/의존스니펫/사용 섹션) · `HANDOFF.md`(모듈·함정 6종·최신세션·우선순위) · `docs/FRAMEWORK_MODULES.md`(진행현황/표/트리/구축순서) · `STACK.md`(의존성 0 명시) · `docs/BASELINE_FEATURES.md`+루트 `BASELINE_FEATURES.md`(#5·#7 ✅ 승격, 우선순위/완료 로그).

## 현재 상태 (적용/검증)
- ✅ **엔진 javac 단독 컴파일 OK**(config 제외 + 실제 core `ErrorCode`/`BusinessException`) + **기능 하니스 26/26 통과**(합성 JPEG APP1 orient 주입: EXIF 1~8 읽기·비율유지 축소·업스케일 금지/허용·PNG→JPEG·메타 제거 후 orient=1·orient6 가로세로 스왑·보정 off 미스왑·알파 평탄화·probe·폭탄 가드·빈입력).
- ⚠️ **config/배선은 작성 환경 Gradle 미검증**(Maven Central 차단) — context·pdf 패턴 verbatim 미러라 위험 낮음. 받는 쪽에서 `:framework:framework-image:test` 로 확인.
- 설계상 arch 규칙 통과 예상: `ImageAutoConfiguration`→`@AutoConfiguration` ✓ / top-level `ImageProperties`→`@ConfigurationProperties` ✓ / 필드주입 0 / Jackson 미사용(수기 JSON도 없음, 순수 바이트) / 슬라이스 image→core 단방향.
- **신규 외부 의존성 0**: 엔진 전부 JDK, web 불필요 → 카탈로그/ext/STACK 버전 무변경.

## 켜는 법
```yaml
framework:
  image:
    enabled: true               # 끄면(기본) 빈 미등록
    default-format: JPEG        # 출력 화이트리스트: JPEG | PNG
    thumbnail-max-edge: 320     # thumbnail() 기본 한 변 상한(px)
    jpeg-quality: 0.85          # 0.0~1.0
    max-source-pixels: 40000000 # 디컴프레션 폭탄 방지(디코드 전 헤더 검사)
```
```java
@Autowired ImageProcessor imageProcessor;
ResizeSpec spec = ResizeSpec.builder().maxEdge(1920).format(ImageFormat.JPEG).build();
byte[] out   = imageProcessor.process(originalBytes, spec);  // 리사이즈+orientation 보정+메타 제거
byte[] thumb = imageProcessor.thumbnail(originalBytes, 200); // 썸네일(한 변 상한)
ImageInfo info = imageProcessor.probe(originalBytes);         // 디코드 없이 크기/포맷
```
- 메타(GPS) 보존이 필요하면 이 모듈을 거치지 말 것 — 출력은 항상 메타 없음.
- 투명 보존이 필요하면 `default-format: PNG`(JPEG 은 알파를 흰 배경으로 평탄화).
- 웹 비의존이라 배치/MQ 컨슈머에서도 토글만 켜면 동작.

## 바로 다음 할 일 (Next)
1. **파일 하드닝 묶음**(카탈로그 #8+#9+#10, `framework-file*` 표면 공유): 대용량/스트리밍(HTTP Range·S3 presigned·멀티파트) · 메타 정합성 강화(확장자↔실제 MIME allowlist·EXIF 정합) · 안티바이러스 훅(`FileScanner` SPI + ClamAV 어댑터 옵트인).
2. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + 멀티모듈 jacoco 집계.
3. **추가 기본기능 대기열 승격**: `docs/BASELINE_FEATURES.md` §6 에 쌓인 **아카이빙/압축** · **일괄 처리(batch processing)** — 착수 시 표로 승격(일괄 처리는 framework-batch 와 경계 정리 필요).
4. (선택) 이미지 심화: WebP/AVIF 출력(ImageIO 플러그인 필요 → 의존성 검토)·워터마크·문서 메타 정합성과 묶어 #9 보강.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **메타(EXIF/GPS) 제거 = 리인코딩 부수효과, 별도 strip 단계 아님**: `process()`/`thumbnail()` 은 디코드→리인코딩이라 출력에 원본 메타가 **항상** 없다(GPS 자동 제거). orientation 만 픽셀에 굽고 나머지 메타는 버린다. 원본 메타 보존 경로가 필요하면 이 모듈 우회.
- **EXIF orientation 보정은 best-effort**: `ExifOrientation.read` 는 APP1 부재·비JPEG·잘림·범위밖·널이면 예외 없이 NORMAL(원본 유지). PNG 등은 orientation 개념 없음. 깨진 EXIF 로 처리 전체가 실패하지 않음(secure-web 수기 파싱과 같은 결).
- **디컴프레션 폭탄은 디코드 전에 차단**: `ImageReader` 로 디코드 없이 헤더 width×height 만 읽어 `max-source-pixels`(기본 40MP) 초과 시 IMAGE_TOO_LARGE. 업로드 **바이트 제한과 별개 방어선**(작은 파일도 픽셀 폭은 거대할 수 있음) — 둘 다 둘 것.
- **JPEG 알파 평탄화**: JPEG 출력 시 투명을 흰 배경으로 합성(검정 박스 방지). 투명 필요하면 PNG. 출력 화이트리스트는 JPEG/PNG 뿐(그 외 UNSUPPORTED_FORMAT).
- **이미지 모듈은 헤드리스·웹 비의존**: AWT 오프스크린만 사용(디스플레이 불필요). `@ConditionalOnWebApplication` 미부착 → 배치/MQ 컨슈머에서도 빈이 뜸. 순수 엔진은 Spring 무의존 → JDK 단독 javac+하니스 검증(레포 빌드 막혀도 핵심 로직 확인 가능).
- (지난·유효) 토글 3단 기본 off / Jackson3(`tools.jackson.*`, `.annotation` 만 예외; 단 image 는 Jackson 미사용) / compileOnly 타입은 test 재선언(image 는 compileOnly 없음=의존성 0) / `.imports` 등록 가드(union 직접 단언) / 신규 모듈은 settings+archtest 추가 / 슬라이스 단방향(image→core) / 필드주입 금지 / 요청 컨텍스트 상속형 ThreadLocal 금지 / 필터는 GlobalExceptionHandler 밖 / Boot4 패키지·외부 라이브러리 리네임 추측 금지 / JUnit launcher·starter-test 모듈마다 / 콘솔 UTF-8(미해결 보류).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증(이번: `ExifOrientation`/`DefaultImageProcessor` 엔진을 javac+하니스로 26/26).
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+테스트가 그 타입 참조 시 `testImplementation` 재선언), BOM 밖 내부 라이브러리=`implementation`(비노출). 이번은 의존성 0(엔진 전부 JDK, web 불필요).
3. `settings.gradle`/`.imports` 등록. 신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가. **새 오토컨피그는 `.imports` 등록 + 등록 가드 테스트**. 신규 top-level 패키지는 슬라이스 충돌/순환 확인(image→core 단방향).
4. 기존 패턴 교차확인 후 미러링(이번: pdf=비웹 처리모듈 오토컨피그/`ApplicationContextRunner`, secure-web=신뢰 못할 바이트 수기 파싱·안전 폴백, context=등록 가드/의존성 0 build.gradle).
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`(웹 전용이면 `@ConditionalOnWebApplication`, 아니면 생략해 배치 사용 허용).
6. **테스트**: 핵심 알고리즘 단위(JDK, 합성 입력 생성) + 오토컨피그 로딩(enabled/disabled, 비웹은 `ApplicationContextRunner`) + 등록 가드.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 + 필요 시 카탈로그 동기화. 사용자 환경에서 `./gradlew :...:test :framework-archtest:test spotlessApply` 검증.

<!-- 갱신 끝 -->
