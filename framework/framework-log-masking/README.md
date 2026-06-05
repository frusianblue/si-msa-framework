# framework-log-masking

개인정보(PII) **로그 마스킹**. 자유 형식 로그 라인·메시지 안에 섞여 흘러든 주민등록번호·휴대폰·카드·이메일을 정규식으로 탐지해 가린다. 표준 3단 토글, 기본 off.

## 왜 별도 모듈인가

framework-core 의 `MaskingUtils` 는 **값을 이미 아는** 필드 단위 마스킹이다(`maskPhone(phone)` 처럼 호출부가 어떤 값인지 안다). 하지만 실무에서 PII 유출은 대개 **자유 텍스트 로그**에서 샌다 — 예외 메시지, 요청 덤프, `log.info("user={}", dto)` 등. 본 모듈은 그 보완으로 **로그 라인 안에 섞인 PII 를 정규식으로 탐지**한 뒤, 실제 마스킹 모양은 core `MaskingUtils` 에 위임해 전사 마스킹 형식을 일치시킨다.

## 두 가지 사용 경로

1. **(1차·확실) `SensitiveDataMasker` 빈 주입** — 감사로그·응답·구조화(JSON) 로그 등에 명시적으로 `mask()` 호출. Logback 패턴을 우회하는 경로(Boot 구조화 로깅 등)까지 커버하는 가장 확실한 방법.
2. **(2차·방어망) Logback `%mmsg` 컨버터** — 패턴 텍스트 로그의 최종 메시지를 자동 마스킹. 누군가 실수로 PII 를 로그에 흘려도 잡아내는 안전망.

> 권장: 민감 데이터를 다루는 지점은 **1차(명시 호출)** 로 확실히 가리고, **2차(컨버터)** 는 전역 방어망으로 함께 켠다.

## 켜는 법

**1단 · 모듈 등록** — `settings.gradle`
```gradle
include 'framework:framework-log-masking'   // 선택형: PII 로그 마스킹이 필요한 프로젝트만
```
프로젝트 `build.gradle`
```gradle
dependencies {
    implementation project(':framework:framework-log-masking')
    // logback-classic 은 Boot 기본 로깅이라 이미 런타임에 존재(추가 의존성 불필요)
}
```

**2·3단 · 기능/구현** — `application.yml`
```yaml
framework:
  log-masking:
    enabled: true            # 끄면(또는 미설정) 빈 미등록 → 아무 동작 안 함
    strip-newlines: true     # CR/LF→공백(로그 인젝션/위조 방지, 기본 true)
    max-length: 0            # 0 이하=무제한, 양수면 그 길이로 절단 후 표식
    install-converter: true  # Logback %mmsg 컨버터용 설치기 등록(기본 true)
    rules:
      rrn: true              # 주민/외국인등록번호
      card: true             # 카드번호
      phone: true            # 휴대폰
      email: true            # 이메일
      account: false         # 계좌(오탐 위험 → 기본 off)
    custom-patterns:         # 사내 식별자 등 추가 정규식(매칭 전체를 별표 마스킹)
      employeeId: "EMP\\d{6}"
```

### 1차 경로 — 빈 주입
```java
private final SensitiveDataMasker masker;   // 생성자 주입(필드 주입 금지 규약)

void audit(SomeDto dto) {
    log.info("AUDIT payload={}", masker.mask(dto.toString()));
}
```

### 2차 경로 — Logback 컨버터
서비스의 `logback-spring.xml` 에서 core 조각과 함께 포함하고 root 를 마스킹 appender 로 교체한다.
```xml
<include resource="logback-common.xml"/>    <!-- 공통 CONSOLE/FILE -->
<include resource="logback-masking.xml"/>   <!-- %mmsg 컨버터 + *_MASKED appender -->

<root level="INFO">
    <appender-ref ref="CONSOLE_MASKED"/>
    <appender-ref ref="FILE_MASKED"/>
</root>
```
`%mmsg` 는 표준 `%msg` 와 동일하나 인자 치환 후 **최종 텍스트**를 마스킹한다(그래서 `{}` 인자로 흘러든 PII 도 잡힌다). 마스킹 규칙은 부팅 시 설치된 `SensitiveDataMasker` 빈이 제공하며, 빈이 아직 없을 때(부팅 초기 로그)도 **내장 기본 규칙으로 폴백**해 원문 노출을 막는다.

> `%msg`/`%message` 단어 자체를 재정의하면 Logback 경고가 날 수 있어 일부러 별도 단어 `%mmsg` 를 쓴다.

## 탐지 규칙과 오탐 정책

자유 로그에서 임의 숫자열을 과하게 가리면 운영 가독성이 떨어진다. 그래서 경계 조건을 엄격히 둔다.

| 규칙 | 형태 | 기본 | 마스킹 결과(core MaskingUtils) |
|---|---|---|---|
| `card` | 4-4-4-4 또는 16연속 | on | `1234-****-****-3456` |
| `rrn` | 6-[1\~8]+6자리 | on | `900101-1******` |
| `phone` | 01[0\|1\|6\|7\|8\|9]-3\~4-4 | on | `010-****-5678` |
| `email` | 표준 이메일 | on | `gi*****@example.com` |
| `account` | 2\~6-2\~6-2\~6 | **off** | `110*******890` |

`account` 는 일반 숫자열과 충돌이 잦아 기본 off — 필요한 프로젝트만 `rules.account=true`. 적용 순서는 자릿수가 긴 카드를 먼저 둬 상호 오삼킴을 막는다(card → rrn → phone → email → account → custom).

> **계좌 규칙을 켤 때 주의**: `account` 정규식은 구분자(`-`)로 묶인 2~6자리 3그룹이면 매칭하므로, **구분자 있는 휴대폰 `010-1234-5678` 같은 비계좌 숫자열도 함께 가린다**(설계상 광범위 — 이것이 기본 off 인 이유). 운영에서 계좌만 정확히 가리려면, 로그에 휴대폰/코드가 계좌와 같은 dash-그룹 형태로 섞이는지 점검하고 필요하면 `custom-patterns` 로 더 좁은 패턴을 쓰는 편이 안전하다.


## 실전 사용 예 (코드)

로그에 남는 민감정보(주민번호/카드/계좌/이메일 등)는 Logback 컨버터가 자동 마스킹한다. 코드에서 직접 마스킹이 필요하면 `SensitiveDataMasker` 빈을 주입한다.
```java
// com.company.framework.logmask.mask.SensitiveDataMasker (빈 자동 등록)
private final SensitiveDataMasker masker;

public void auditDump(String payload) {
    log.info("수신: {}", masker.mask(payload));   // 010-1234-5678 → 010-****-5678 등
}
```
```yaml
framework.log-masking:
  enabled: true
  strip-newlines: true   # 로그 인젝션 방지(개행 제거)
  max-length: 2000
```

## 한계 / 주의

- **Boot 구조화 로깅(observability 모듈의 JSON 로그)** 은 `PatternLayout` 을 우회하므로 `%mmsg` 컨버터가 적용되지 않는다. 이 경우 **1차 경로(`SensitiveDataMasker` 빈 명시 호출)** 로 마스킹해야 한다.
- 정규식 기반이라 **완벽한 탐지는 보장하지 않는다**(방어망 성격). 민감 지점은 1차 경로로 확실히 가린다.
- 신규 외부 의존성 0(logback-classic 은 Boot 기본 로깅, `compileOnly` 비노출).
