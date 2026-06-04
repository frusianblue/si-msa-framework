# SECURITY_VALIDATION_ADDITIONS.md — JWT 운영 가드 / 요청 검증 보강

이 묶음에 추가된 보안·검증 변경 2건. **새 외부 의존성 0** (validation 은 이미 `framework-core` 가
`api 'spring-boot-starter-validation'` 로 전이 노출 중 → 모든 서비스 컴파일 클래스패스에 존재).

---

## 1. JWT 시크릿 운영 가드 (`JwtSecretSafetyGuard`) — 신규

### 무엇이 없었나
`JwtProvider` 는 시크릿을 그대로 HMAC 키로 썼고, **운영에서 기본/약한 시크릿을 막는 장치가 없었다.**
prod 프로파일이 `secret: ${JWT_SECRET}` 로 주입을 강제하긴 하나, "주입은 됐지만 값이 레포 기본
placeholder 그대로"인 경우는 걸러지지 않았다.

### 무엇을 추가했나
`framework-security` 에 `JwtSecretSafetyGuard`(기존 `DevAuthSafetyGuard`/`PasswordSafetyGuard` 와 동일한
`InitializingBean` 패턴). `SecurityAutoConfiguration` 에 빈 등록.

- **prod / production 프로파일**에서 아래면 **부팅 실패**(`IllegalStateException`):
  - 시크릿이 비어 있음
  - 레포 기본 placeholder(`change-this-...`) 또는 `change-this`/`change-me` 흔적이 남음
  - HS256 최소 길이(32바이트) 미만
- **local / dev**: 같은 조건이면 경고 배너만(개발 편의 유지).

### 어떻게 쓰나
운영 배포 시 `JWT_SECRET` 환경변수에 32바이트 이상의 강한 키를 주입하면 끝. 미교체/약한 키로 뜨면
기동이 막혀 사고를 조기에 잡는다. 별도 설정 불필요(자동 적용).

> 참고: 같은 위험이 `framework.crypto.aes-secret`(`AES_SECRET`)에도 있다. 필요하면 동일 패턴으로
> 가드를 하나 더 둘 수 있다(이번엔 요청대로 JWT 만 적용).

### 테스트
`JwtSecretSafetyGuardTest` 6케이스(prod 기본/빈/짧음/change-me → 실패, prod 강한키 → 통과, local 약한키 → 통과).

---

## 2. 요청 검증(Validation) 보강

### 현황 (오해 정정)
검증이 **아예 없던 게 아니다.** 이미:
- `framework-core` 에 `spring-boot-starter-validation`(api)
- 여러 DTO/컨트롤러가 `@Valid` 사용(`UserController`, `CommonCodeController`, admin 컨트롤러 등)
- `GlobalExceptionHandler` 가 `MethodArgumentNotValidException`(@Valid 바디)·`ConstraintViolationException`
  ·`HttpMessageNotReadableException`·타입불일치·필수파라미터 누락 등을 표준 `ApiResponse` 로 변환

### 실제 빈틈 2가지 → 보강
1. **`HandlerMethodValidationException` 미처리 (Spring Framework 6.1+/7)**
   - 컨트롤러 **메서드 파라미터**(`@RequestParam`/`@PathVariable` + 제약) 검증 실패 시 6.1+ 는
     `HandlerMethodValidationException`(=`ResponseStatusException`)을 던진다. 이걸 안 잡으면
     우리 `ApiResponse` 포맷이 아닌 **기본 ProblemDetail** 로 응답돼 형식이 깨졌다.
   - → `GlobalExceptionHandler` 에 핸들러 추가(→ `INVALID_INPUT` 400, ApiResponse 포맷 통일).
   - (참고: `@Valid @RequestBody` 는 여전히 `MethodArgumentNotValidException` → 기존 핸들러가 처리.)
2. **미인증 진입점(로그인)에 제약 없음**
   - `LoginCommand`(loginId/password)에 제약이 없어 **빈 자격증명도 통과**했다.
   - → `@NotBlank` 추가 + `AuthController.login` 에 `@Valid`. 빈 값이면 400 으로 조기 차단.

### 어떻게 쓰나 (앱에서 새 DTO 검증하는 표준 패턴)
```java
public record CreateXxxRequest(
        @NotBlank(message = "이름은 필수입니다.") String name,
        @Email String email,
        @Min(0) int amount) {}

@PostMapping("/xxx")
public ApiResponse<Void> create(@Valid @RequestBody CreateXxxRequest req) { ... }

// 메서드 파라미터 검증이면 클래스에 @Validated + 파라미터에 제약
@Validated
@RestController
class XxxController {
    @GetMapping("/xxx")
    public ApiResponse<?> list(@RequestParam @Min(1) int page) { ... }
}
```
검증 실패는 전부 `GlobalExceptionHandler` 가 `ApiResponse.fail("E0001", ...)` 400 으로 변환한다.

### 빠른 확인
```bash
# 로그인 빈 값 → 400 (예전엔 통과해 내부 로직까지 갔음)
curl -i -X POST http://localhost:8080/api/v1/auth/login \
     -H 'Content-Type: application/json' -d '{"loginId":"","password":""}'
```

---

## 변경 파일
```
[신규] framework/framework-security/.../jwt/JwtSecretSafetyGuard.java
[신규] framework/framework-security/.../jwt/JwtSecretSafetyGuardTest.java (test)
[수정] framework/framework-security/.../config/SecurityAutoConfiguration.java   (가드 빈 등록)
[수정] framework/framework-security/.../auth/AuthController.java                (@Valid)
[수정] framework/framework-security/.../auth/LoginCommand.java                  (@NotBlank)
[수정] framework/framework-core/.../error/GlobalExceptionHandler.java           (HandlerMethodValidationException)
```

## 검증 커맨드(사용자 환경)
```bash
./gradlew :framework:framework-security:test :framework:framework-core:test \
          :framework:framework-archtest:test spotlessApply
```
