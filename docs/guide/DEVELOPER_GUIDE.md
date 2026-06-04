# 개발자 가이드 (Developer Guide)

> 이 프레임워크를 **받은 업무 개발자**가 매일 쓰는 표준 사용법. (🚧 다음 단계에서 본문 작성 — 아래는 목차 골격)

## 작성 예정 목차

1. **표준 응답 / 예외**
   - `ApiResponse<T>` 로 응답 감싸기, `GlobalExceptionHandler` 가 잡는 예외 던지기
   - 비즈니스 예외(`BusinessException` + `ErrorCode`) 정의·사용
2. **인증 / 인가**
   - `Authorization: Bearer <JWT>` 흐름, `@PreAuthorize`/RBAC 메뉴-권한 매핑
   - `CurrentUser` 로 로그인 사용자 꺼내기
3. **페이징 / 정렬** — 표준 요청·응답 포맷
4. **MyBatis 매퍼 작성** — 감사필드 자동주입, 암호화 타입핸들러, `@MapperScan` 규약(`annotationClass = Mapper.class`)
5. **공통 util 사용** — 검증·마스킹·날짜/영업일·금액·한글·고정폭전문(CP949)
6. **선택 모듈 호출 예** — file 업/다운, excel, pdf, idgen 채번, notification, i18n 메시지
7. **로깅 / traceId** — MDC 활용, 로그 PII 마스킹
8. **설정값 암호화** — `ENC(...)` 사용법 ([`../reference/ENCRYPTION_GUIDE.md`](../reference/ENCRYPTION_GUIDE.md))
9. **테스트 작성 규약** — 슬라이스 테스트, `compileOnly→testImplementation` 함정
10. **하지 말 것** — `com.fasterxml.*`(Jackson3 금지), 필드주입, 모듈 순환

> 모듈을 무엇을 켤지는 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md), 복붙 샘플은 [`SAMPLES.md`](SAMPLES.md).
