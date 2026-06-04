# framework-archtest

**테스트 전용** 모듈 — ArchUnit 으로 아키텍처/레이어/네이밍/순환 규칙을 강제한다. **배포 산출물이 아니다**(런타임/배포 무영향).

## 켜는 법
별도 토글 없음. 빌드 시 자동 검사:
```bash
./gradlew :framework:framework-archtest:test
```
전 라이브러리 모듈의 `main` 을 `testImplementation project(...)` 로 임포트해 한곳에서 검사한다.

> ⚠️ **새 라이브러리 모듈을 추가하면 `framework-archtest/build.gradle` 에 `testImplementation project(':framework:framework-<new>')` 한 줄을 반드시 추가**한다. 누락 시 그 모듈은 규칙 검사 사각지대가 된다.

## 검사하는 규칙 (7종)
- 모듈(슬라이스) **순환 의존 금지**
- **Jackson 3 규약** — `tools.jackson.*` 만, 이동된 `com.fasterxml.jackson.*` 금지(`.annotation` 예외)
- mapper / domain **레이어 격리**
- `*AutoConfiguration` / `*Properties` **네이밍** 규칙
- **필드 주입 금지**(생성자 주입 강제)
- `@AnalyzeClasses`(DoNotIncludeTests) 로 main 만 분석

## 쓰는 법
규칙 위반 시 빌드(테스트) 실패로 드러난다. 새 규칙은 ArchUnit 테스트 클래스에 추가.

## 끄는 법
끄지 않는다(CI 게이트). 단, 산출물에는 포함되지 않으므로 런타임 영향 없음.

## 버전 관리
ArchUnit·WireMock 은 **test 전용**. 신규 런타임 의존성 없음.
