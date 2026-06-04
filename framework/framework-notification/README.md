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

## 끄는 법
`framework.notification.enabled: false` 또는 채널별 `enabled: false`, 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`SmsClient`/`AlimtalkClient`/`NotificationSender` SPI 빈 등록 시 기본 로깅 구현이 양보(`@ConditionalOnMissingBean`).

## 버전 관리
spring-boot-starter-mail 은 Boot BOM. SMS/알림톡 벤더 SDK 는 호스트 프로젝트가 추가. 모듈 자체 신규 의존성 없음.
