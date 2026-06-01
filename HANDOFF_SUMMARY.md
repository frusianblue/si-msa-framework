# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**framework-notification** 완성 — **메일/SMS/알림톡 채널 추상화**. `NotificationService` 가 `ChannelType` 으로 라우팅하고, 채널별 `NotificationSender` 를 토글로 켠다. 메일=Spring `JavaMailSender`, SMS·알림톡=**벤더 어댑터 SPI**(`SmsClient`/`AlimtalkClient`) + **기본 로깅 구현**(실발송X, 서비스가 벤더 빈으로 `@ConditionalOnMissingBean` 교체). 이로써 **업무 생산성 3종(excel·batch·notification) 완료**. 새 외부 의존성 0.

## 최종 갱신
- 일자: 2026-06-01 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 `framework/framework-notification/`** ([선택], `framework.notification.enabled=false` 기본). **새 외부 의존성 0**(spring-boot-starter-mail = Boot BOM).
  - **SPI 패턴(framework-file 과 동형)**: `NotificationSender`(channelType+send) 구현을 채널별로 등록 → `NotificationService` 가 `EnumMap<ChannelType,Sender>` 로 모아 라우팅. 미활성 채널 호출 시 `NotificationException`.
  - 공개 모델: `ChannelType`(MAIL/SMS/ALIMTALK) · `NotificationRequest`(빌더: channel/to 필수, subject/content/html/from/templateCode/variables) · `NotificationResult`(success+detail, best-effort) · `NotificationException`(구성/디스패치 오류 전용).
  - **메일**: `MailNotificationSender`(JavaMailSender + MimeMessageHelper, 텍스트/HTML, from override→기본값). `channels.mail.enabled` + `@ConditionalOnBean(JavaMailSender)`(=spring.mail.host).
  - **SMS**: `SmsClient` SPI + 기본 `LoggingSmsClient` + `SmsNotificationSender`. `channels.sms.enabled`.
  - **알림톡**: `AlimtalkClient` SPI(템플릿코드/변수/senderKey) + 기본 `LoggingAlimtalkClient` + `AlimtalkNotificationSender`. `channels.alimtalk.enabled`.
  - 오토컨피그 `NotificationAutoConfiguration` — `@AutoConfiguration(afterName=Boot MailSenderAutoConfiguration)` + master `@ConditionalOnProperty(notification.enabled)`. 채널별 nested `@Configuration` 가 각 토글로 sender/client 빈 제공(client 는 `@ConditionalOnMissingBean(타입)` → 벤더 교체).
- **등록/문서**: `settings.gradle`(notification include) · `STACK.md` 3.2(spring-boot-starter-mail 행) · `docs/FRAMEWORK_MODULES.md`(2.4 ✅ ×3 + 진행현황). **libs.versions.toml/루트 build.gradle 무변경**(버전 0).

## 현재 상태 (적용/검증)
- 신규 파일 모두 repo 반영. 정적 점검 통과(괄호 균형, 패키지=디렉터리, Jackson2 import 0, javax.mail import 0).
- **메일 API 를 GitHub 소스로 확정**: Spring 7 메일 = `org.springframework.mail.javamail.{JavaMailSender,MimeMessageHelper}` + `jakarta.mail.*`(javax 아님). Boot 4 메일 자동구성 = `org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration`(spring.mail.host 설정 시 JavaMailSender 빈).
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 차단). 받는 쪽: `./gradlew :framework:framework-notification:compileJava` + `./gradlew spotlessApply`.

## 켜는 법 (application.yml)
```yaml
framework:
  notification:
    enabled: true
    channels:
      mail:     { enabled: true, from: noreply@company.com }   # spring.mail.* 필요
      sms:      { enabled: true, from: "0212345678" }          # 기본 로깅 → 벤더 SmsClient 빈 등록 시 교체
      alimtalk: { enabled: true, sender-key: ${ALIMTALK_SENDER_KEY:} }
spring:
  mail: { host: smtp.x.com, port: 587, username: u, password: p }
```
사용: `NotificationService` 주입 → `send(NotificationRequest.mail(to,subject,content).html(true).build())` / `.sms(to,text)` / `.alimtalk(to,templateCode).variable(...)`. 벤더 연동: `SmsClient`/`AlimtalkClient` 빈을 서비스에 정의하면 기본 로깅 구현을 자동 대체.

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 **notification 모듈 컴파일 확인** + `spotlessApply`. 메일 실발송하려면 `spring.mail.*` 설정.
2. **다음 묶음 선택**:
   - **messaging 소비자측** — 멱등 소비(컨슈머): Kafka 헤더 `x-event-id` 키로 기존 framework-idempotency 연계(직전 Outbox 발행과 짝).
   - **규제특화**(pki/mfa/hsm/recon/egov) 또는 **관측(observability)**.
3. 이후: 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 `docs/FRAMEWORK_MODULES.md` 4절)

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **Spring 6/7 메일은 `jakarta.mail.*`**(javax.mail 아님). `MessagingException`/`MimeMessage` 모두 jakarta. 메일 클래스는 `org.springframework.mail.javamail.*`(spring-context-support, starter-mail 이 전이).
- **Boot 4 메일 자동구성도 패키지 분리**: `org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration`(jdbc/batch/quartz 선례 동일). JavaMailSender 빈은 `spring.mail.host`(또는 jndi-name) 설정 시에만 생성 → 메일 sender 는 `@ConditionalOnBean(JavaMailSender)` 로 우아하게 비활성.
- **벤더 SPI + 기본 로깅구현 패턴**(framework-file 의 FileStorage 동형): 프레임워크는 `LoggingSmsClient`/`LoggingAlimtalkClient`(실발송X)만 제공, 서비스가 동일 타입 빈 등록 시 `@ConditionalOnMissingBean(타입)` 로 교체. "SMS 켰는데 조용히 로그만" 가능 → 운영 점검 포인트(벤더 빈 등록 확인).
- **알림은 best-effort**: 발송 실패는 예외 대신 `NotificationResult.failure` 로 반환(호출부가 정책 결정). 구성/채널 부재만 `NotificationException`.
- (기존) 새 모듈 `settings.gradle` 등록 필수 · BOM 밖 새 라이브러리만 카탈로그 핀(이번 0) · 트랜잭션매니저 새 정의 금지 · SXSSF 종료 `close()`(dispose deprecated) · Batch6 패키지 이동(core.job/core.job.parameters/core.launch) & JobLauncher→JobOperator.

## 모듈 추가 레시피 (검증된 반복 절차)
1. `framework/framework-<X>/` 생성: `config`(Properties+AutoConfiguration) · 도메인/채널 패키지 · `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(FQCN).
2. `build.gradle`: `api project(':framework:framework-core')` + starter 는 능력 전이=`api`(mail/batch/quartz/kafka), 내부구현=`implementation`(POI), 호스트제공=`compileOnly`(web/jdbc). **BOM 밖 새 라이브러리만** 카탈로그+ext 핀.
3. **`settings.gradle` include 추가**(잊지 말 것).
4. 코드 전 **Boot4/Spring7/Jackson3 + 외부 라이브러리 API 를 공식 소스(GitHub raw)로 확정**(메이저 버전업=패키지 이동 잦음, 컴파일 미검증 환경).
5. 오토컨피그: `@AutoConfiguration(afterName=Boot autoconfig)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. 교체형 SPI 는 `@ConditionalOnMissingBean(타입)` 으로 기본구현 제공. 외부 빈 의존은 `@ConditionalOnBean`(순서 취약 시 property 게이팅).
6. 검증: `./gradlew :framework:framework-<X>:compileJava` (+`spotlessApply`).
7. 드롭인: 모듈 폴더 + 변경 파일(완성 `settings.gradle`/필요 시 카탈로그·루트 build.gradle·문서) → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
