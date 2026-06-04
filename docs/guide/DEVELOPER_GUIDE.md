# 개발자 가이드 (Developer Guide)

이 프레임워크를 **받은 업무 개발자**가 매일 쓰는 표준 사용법. 모듈을 무엇을 켤지는 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md), 복붙 샘플은 [`SAMPLES.md`](SAMPLES.md), 처음 기동은 [`GETTING_STARTED.md`](GETTING_STARTED.md).

> 패키지 루트는 `com.company.framework.*`. 아래 import 경로는 그 기준이다.

---

## 0. 표준 규약 한눈에

- 컨트롤러 반환은 **항상 `ApiResponse<T>`**. 직접 `ResponseEntity` 를 만들 일은 거의 없다.
- 비즈니스 오류는 **`BusinessException` 던지기**. try/catch 로 HTTP 상태를 직접 만들지 않는다 — `GlobalExceptionHandler` 가 변환한다.
- 목록은 **`PageResponse<T>`**, 요청은 `PageRequest`/`SearchCondition`.
- 현재 사용자·감사필드는 **자동** — 직접 `created_by` 를 채우지 않는다.
- JSON 은 **Jackson 3**(`tools.jackson.*`). `com.fasterxml.*` 는 컴파일 실패(ArchUnit 차단).
- 주입은 **생성자 주입**(필드 주입 금지, ArchUnit 차단).

---

## 1. 표준 응답 — `ApiResponse<T>`

```java
import com.company.framework.core.response.ApiResponse;

@GetMapping("/{id}")
public ApiResponse<UserResponse> get(@PathVariable Long id) {
    return ApiResponse.ok(userService.get(id));
}

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest req) {
    return ApiResponse.ok(userService.create(req), "사용자가 생성되었습니다.");  // 메시지 동반
}

@PatchMapping("/me/password")
public ApiResponse<Void> changePw(@Valid @RequestBody PasswordChangeRequest req) {
    userService.changeMyPassword(req);
    return ApiResponse.ok(null, "비밀번호가 변경되었습니다.");
}
```
응답 형태(record): `{ success, code, message, data, timestamp }`. `traceId` 는 응답 헤더로 노출된다.

---

## 2. 예외 처리 — `BusinessException` + `ErrorCode`

오류는 잡지 말고 던진다. `GlobalExceptionHandler` 가 `ErrorCode` 의 HTTP 상태로 통일 변환한다.

```java
import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;

throw new BusinessException(ErrorCode.Common.NOT_FOUND);
throw new BusinessException(ErrorCode.Common.CONFLICT, "이미 존재하는 로그인ID입니다: " + id);
```

공통 코드(`ErrorCode.Common`): `INTERNAL_ERROR`(500) · `INVALID_INPUT`(400) · `UNAUTHORIZED`(401) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `CONFLICT`(409) · `LOGIN_LOCKED`(429).

**업무 도메인 코드는 직접 enum 으로 정의**(인터페이스 구현):
```java
public enum OrderError implements ErrorCode {
    OUT_OF_STOCK("O1001", "재고가 부족합니다.", HttpStatus.CONFLICT),
    ORDER_CLOSED("O1002", "마감된 주문입니다.", HttpStatus.BAD_REQUEST);
    // code(), message(), httpStatus() 구현 — ErrorCode.Common 형태 그대로
}
throw new BusinessException(OrderError.OUT_OF_STOCK);
```
`@Valid` 바인딩 오류는 자동으로 `INVALID_INPUT` 으로 정리된다.

---

## 3. 페이징 / 정렬

```java
import com.company.framework.core.page.*;

// 컨트롤러
@GetMapping
public ApiResponse<PageResponse<UserResponse>> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(userService.list(PageRequest.of(page, size)));
}

// 서비스
PageRequest req = PageRequest.of(page, size);      // null 안전 기본값 보정
long total = mapper.count(cond);
List<UserResponse> rows = mapper.find(cond, req.offset(), req.size());
return PageResponse.of(rows, req, total);          // totalPages/hasNext 계산
```
검색+정렬은 `SearchCondition`(page/size/sortBy/sortDirection/keyword)을 `@ModelAttribute` 로 받고:
```java
String orderBy = cond.toSafeOrderBy(Set.of("name", "createdAt"), "createdAt"); // 화이트리스트 — SQL 인젝션 차단
PageRequest req = cond.toPageRequest();
```
> 정렬 컬럼은 반드시 화이트리스트(`toSafeOrderBy` / `SecureUtils.safeOrderColumn`). 사용자 입력을 ORDER BY 에 직접 넣지 않는다.

---

## 4. 인증 / 인가

**로그인** — `POST /api/v1/auth/login {loginId,password}` → JWT. 이후 모든 호출에 `Authorization: Bearer <JWT>`.

**현재 사용자** — `CurrentUserProvider` 주입:
```java
import com.company.framework.mybatis.support.CurrentUserProvider;

private final CurrentUserProvider currentUser;   // 생성자 주입

String loginId = currentUser.getCurrentUser()
        .orElseThrow(() -> new BusinessException(ErrorCode.Common.UNAUTHORIZED));
```
(같은 provider 가 MyBatis 감사필드 자동주입에도 쓰인다 — 5장.)

**인가** — URL↔권한은 DB 기반 RBAC 가 동적으로 매핑(`framework.security.dynamic-authorization`). 메서드 단위는 `@PreAuthorize`:
```java
@PreAuthorize("hasRole('ADMIN')")
@PatchMapping("/{id}/password/reset")
public ApiResponse<Void> reset(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest req) { ... }
```
인증/RBAC/토큰 정책 상세는 [`framework-security/README.md`](../../framework/framework-security/README.md), 토큰 검증은 [`../reference/TOKEN_VERIFICATION_GUIDE.md`](../reference/TOKEN_VERIFICATION_GUIDE.md).

---

## 5. MyBatis 매퍼

**감사필드 자동주입** — 엔티티가 `BaseEntity`(`createdAt/createdBy/updatedAt/updatedBy`)를 상속하면 INSERT/UPDATE 시 `AuditFieldInterceptor` 가 현재 사용자/시각으로 자동 채운다. 개발자가 직접 set 하지 않는다.
```java
public class Order extends BaseEntity { /* 업무 컬럼만 */ }
```
**컬럼 암호화** — 민감 컬럼은 `EncryptedStringTypeHandler` 로 AES-GCM 저장/복호화:
```xml
<result property="rrn" column="rrn"
        typeHandler="com.company.framework.mybatis.handler.EncryptedStringTypeHandler"/>
```
**매퍼 스캔** — 부트 클래스에 매퍼 패키지를 지정:
```java
@SpringBootApplication
@MapperScan("com.company.order.mapper")   // @Mapper 인터페이스만 있는 패키지면 이대로 OK
```
> 매퍼 패키지에 `@Mapper` 가 아닌 인터페이스(SPI 등)가 섞여 있으면 `annotationClass = org.apache.ibatis.annotations.Mapper.class` 를 지정해 스캔 범위를 좁힌다(미지정 시 `ConflictingBeanDefinitionException` — framework-commoncode/file 등 일부 모듈이 그 경우). 카멜케이스 매핑(`user_name`↔`userName`)은 자동.

---

## 6. 공통 util (`com.company.framework.core.util`)

토글 없는 정적 유틸. 자주 쓰는 것:

| 클래스 | 대표 메서드 | 용도 |
|---|---|---|
| `ValidationUtils` | `isEmail` · `isMobile` · `isPhone` · `isValidCardNumber` | 형식 검증 |
| `KoreanRegNoUtils` | `isValidResidentNo` · `isValidBusinessNo` · `isValidCorporateNo` | 주민/사업자/법인번호 검증 |
| `MaskingUtils` | `maskName` · `maskEmail` · `maskPhone` · `maskResidentNo` · `maskCard` · `maskAccount` | PII 마스킹 |
| `DateUtils` / `HolidayUtils` | `formatDash` · `isBusinessDay` · `nextBusinessDay` · `plusBusinessDays` | 날짜·영업일 |
| `MoneyUtils` | `comma` · `toKoreanAmount` · `round` · `truncate` · `ceil` | 금액 표기/반올림 |
| `HangulUtils` | `chosung` · `matchesChosung` · `josa` · `endsWithBatchim` | 한글 초성검색·조사 |
| `FixedWidthUtils` | `fit` · `textField` · `numberField` · `field` | 고정폭 전문(CP949/EUC-KR) |
| `CsvUtils` | `writeRow` · `writeRows` · `parse` | CSV |
| `HashUtils` | `sha256Hex` · `base64Encode` | 해시/인코딩 |
| `JsonUtils` | `toJson` · `fromJson` · `mapper()` | JSON(**Jackson 3** `JsonMapper`) |
| `SecureUtils` | `sanitizeFileName` · `safeOrderColumn` · `safeOrderDirection` | 시큐어 보조 |

```java
if (!KoreanRegNoUtils.isValidResidentNo(rrn)) throw new BusinessException(ErrorCode.Common.INVALID_INPUT);
String masked = MaskingUtils.maskResidentNo(rrn);          // 900101-1******
LocalDate payday = HolidayUtils.nextBusinessDay(date, holidays);
String amount = MoneyUtils.comma(1234567);                  // "1,234,567"
```

---

## 7. 선택 모듈 호출 (요약)

켜진 모듈만 빈이 등록된다(켜는 법은 각 README). 대표 사용:

- **파일** — `FileService.upload/download/downloadRange` → [`framework-file/README.md`](../../framework/framework-file/README.md)
- **Excel** — `ExcelExporter.write(out, sheet, cols, rows)`(스트리밍) → [`framework-excel/README.md`](../../framework/framework-excel/README.md)
- **채번** — `framework-idgen`(Snowflake/업무코드) → [`framework-idgen/README.md`](../../framework/framework-idgen/README.md)
- **알림** — `NotificationService.send(req)` → [`framework-notification/README.md`](../../framework/framework-notification/README.md)
- **메시지/다국어** — `framework-i18n`(MessageSource) → [`framework-i18n/README.md`](../../framework/framework-i18n/README.md)
- **외부 API** — `framework-client`(타임아웃/재시도/서킷) → [`framework-client/README.md`](../../framework/framework-client/README.md)
- **멱등/Outbox/Saga** — 금융 연계 → [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md) §3

---

## 8. 로깅 / traceId

요청마다 `traceId` 가 MDC 에 부여돼 로그 패턴과 응답 헤더에 찍힌다(`framework.core.trace`). 별도 코드 불필요. 로그는 표준 SLF4J:
```java
private static final Logger log = LoggerFactory.getLogger(OrderService.class);
log.info("주문 생성 orderId={}", orderId);
```
> PII 는 로그에 평문으로 남기지 말 것. `framework-log-masking` 을 켜면 자유 텍스트 로그의 PII 가 자동 마스킹된다.

---

## 9. 설정값 암호화 (`ENC(...)`)

yaml 의 시크릿을 `ENC(...)` 로 두면 기동 시 자동 복호화된다.
```yaml
spring:
  datasource:
    password: ENC(abc123...base64...)
```
토큰 생성: `AES_SECRET=... java -cp <cp> com.company.framework.core.crypto.CryptoCli encrypt '평문'`. 마스터키(`framework.crypto.aes-secret`)는 항상 평문 주입. 상세 [`../reference/ENCRYPTION_GUIDE.md`](../reference/ENCRYPTION_GUIDE.md).

---

## 10. 테스트 작성 규약

- 단위 테스트는 표준 JUnit5 + `spring-boot-starter-test`. 별도 launcher 의존성 추가 불필요(루트에서 일괄 제공).
- **`compileOnly` 의존을 테스트에서 쓰면 `testImplementation` 으로 다시 선언**해야 한다. 특히 `ApplicationContextRunner` 로 오토컨피그를 로딩하는 테스트는 그 설정 클래스의 `@Bean` 파라미터/반환 타입을 전부 로드하므로(`@ConditionalOnClass` 무관) `compileOnly`(web/jdbc/redis 등)를 모두 재선언해야 컨텍스트가 뜬다.
- 외부 연동은 WireMock(standalone), DB 통합은 Testcontainers-PostgreSQL.

---

## 11. 하지 말 것 (ArchUnit 이 빌드에서 차단)

- ❌ `com.fasterxml.jackson.*` import — **Jackson 3** 는 `tools.jackson.*`(`.annotation` 패키지만 예외). JSON 은 `JsonUtils`/`JsonMapper`.
- ❌ 필드 주입(`@Autowired` 필드) — **생성자 주입**만.
- ❌ 모듈 간 순환 의존 · mapper↔domain 레이어 침범.
- ❌ `*AutoConfiguration`/`*Properties` 네이밍 위반.
- ❌ 사용자 입력을 ORDER BY/파일경로에 직접 사용 — `SecureUtils`/`toSafeOrderBy` 경유.
- ❌ 감사필드(`created_by` 등) 수동 set, HTTP 상태 직접 생성(예외로 위임).

위반 시 `./gradlew :framework:framework-archtest:test` 에서 실패한다. 규약 배경은 [`../reference/CHANGES_AND_DEPRECATIONS.md`](../reference/CHANGES_AND_DEPRECATIONS.md), 실제 겪은 함정 모음은 [`PITFALLS.md`](PITFALLS.md).
