# framework-notification

메일/SMS/알림톡 **채널 추상화**. `NotificationService` 가 채널 라우팅하고, 메일은 JavaMailSender, SMS·알림톡은 벤더 SPI(기본 로깅 구현 → 교체).

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-notification') }   // spring-boot-starter-mail 전이
```
```yaml
framework:
  notification:
    enabled: true            # 기본 false
    channels:
      mail:     { enabled: true,  from: no-reply@corp.com }
      sms:      { enabled: false, from: "0212345678" }
      alimtalk: { enabled: false, sender-key: ${ALIMTALK_KEY} }
spring:
  mail: { host: ..., port: 587, username: ..., password: ... }   # mail 채널 사용 시
```

## 쓰는 법
```java
private final NotificationService notifications;

NotificationResult r = notifications.send(
    NotificationRequest.builder()
        .channel(ChannelType.MAIL)
        .to("user@corp.com").subject("알림").body("내용").build());
```
- SMS/알림톡 벤더 연동: `SmsClient`/`AlimtalkClient` 를 구현해 빈 등록(미구현 시 `Logging*Client` 로 로깅만).


## 실전 사용 예 (코드)

메일/SMS/알림톡을 단일 `NotificationService.send(...)` 로 보낸다. 요청은 채널별 정적 빌더로 만든다.
```java
// com.company.framework.notification.{NotificationService, NotificationRequest}
private final NotificationService notifications;

notifications.send(NotificationRequest.mail("user@corp.com", "가입 완료", "<b>환영합니다</b>")
        .html(true).build());

notifications.send(NotificationRequest.sms("01012345678", "[회사] 인증번호 123456").build());

notifications.send(NotificationRequest.alimtalk("01012345678", "WELCOME_001")
        .variable("name", "홍길동").build());   // 템플릿 변수
```
채널 구현(SMS/알림톡 클라이언트)은 미설정 시 로깅 구현으로 대체되어 개발 환경에서 안전하게 동작한다.

## 끄는 법
`framework.notification.enabled: false` 또는 채널별 `enabled: false`, 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`SmsClient`/`AlimtalkClient`/`NotificationSender` SPI 빈 등록 시 기본 로깅 구현이 양보(`@ConditionalOnMissingBean`).

## 버전 관리
spring-boot-starter-mail 은 Boot BOM. SMS/알림톡 벤더 SDK 는 호스트 프로젝트가 추가. 모듈 자체 신규 의존성 없음.
