# framework-i18n (메시지 외부화 / 다국어)

`MessageSource` + 현재 로케일 파사드(`MessageResolver`)와, `ErrorCode` 메시지의 로케일별 해석을 제공한다.
기존 core `GlobalExceptionHandler` 를 **수정하지 않고**, 우선순위 높은 advice 로 `BusinessException` 메시지만 i18n 처리한다.

## 켜는 법
**1단 · 모듈** — `settings.gradle` 에 `include 'framework:framework-i18n'` (INSTALL 참고),
사용할 서비스 `build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-i18n') }
```
**2·3단 · 기능/설정** — `application.yml`:
```yaml
framework:
  i18n:
    enabled: true
    basenames: [classpath:messages, classpath:messages-common]  # 앞쪽이 우선(프로젝트가 override)
    default-locale: ko
    encoding: UTF-8
    cache-seconds: -1            # 개발 중엔 5 로 두면 리로드
    error-localization: true     # BusinessException 메시지 로케일 해석
```

## 쓰는 법
**(1) 에러 메시지 다국어** — 서비스 `src/main/resources/messages.properties` / `messages_en.properties` 에
`error.<code>` 키만 추가하면 끝(코드/HTTP 상태는 그대로).
```properties
# messages.properties (ko)
error.U1001=이미 존재하는 사용자입니다.
# messages_en.properties
error.U1001=User already exists.
```
```java
throw new BusinessException(myErrorCode);   // Accept-Language: en → "User already exists."
```
키가 없으면 자동으로 `ErrorCode.message()` 로 폴백한다.

**(2) 일반 메시지** — 어디서나 주입해 사용:
```java
private final MessageResolver messages;
String msg = messages.get("user.welcome", userName);   // user.welcome=환영합니다, {0}님
```

**(3) 로케일 결정** — `Accept-Language` 헤더로 자동 선택, 없으면 `default-locale`.

## 끄기 / override
- `framework.i18n.enabled: false` 또는 의존성 제거 → 빈 미등록, core 기본 동작으로 복귀.
- 프로젝트가 `messageSource` / `localeResolver` 빈을 직접 등록하면 `@ConditionalOnMissingBean` 으로 양보.
- advice 만 끄려면 `framework.i18n.error-localization: false`.

## 번들 위치
- framework 기본 공통 에러: 이 모듈 jar 의 `messages-common[_en].properties`.
- 프로젝트 메시지: 각 서비스 `classpath:messages[_xx].properties` (없어도 됨 — 안전하게 스킵).
- 새 외부 라이브러리 없음 → `libs.versions.toml` / `STACK.md` 변경 불필요.
