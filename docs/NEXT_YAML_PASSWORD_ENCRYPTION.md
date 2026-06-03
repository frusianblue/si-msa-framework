# NEXT_YAML_PASSWORD_ENCRYPTION.md — 다음 세션 작업 설계서 (설정값/패스워드 암호화)

> 목적: 다음 세션 최우선 작업인 **YAML(설정값) 패스워드 암호화**를 막힘 없이 진행하기 위한 결정·설계·함정·체크리스트.
> 결론 한 줄: **Jasypt 도입하지 말고**, 이미 있는 `AesCryptoService` + Boot4 `EnvironmentPostProcessor` 로
> `ENC(...)` 프로퍼티를 복호화한다. **신규 외부 의존성 0, Jackson 무관**.

---

## 1. 무엇을 만들 것인가
`application*.yml`(또는 env/시스템 프로퍼티) 안의 민감값을 평문 대신 암호문으로 두고, 기동 시 자동 복호화:
```yaml
spring:
  datasource:
    password: ENC(Qk9k...Base64...)     # 평문 대신
framework:
  security:
    jwt:
      secret: ENC(...)                   # jwt 시크릿도 가능(아래 §6 가드와 연동)
```
복호화 키(마스터 키)는 **`framework.crypto.aes-secret`**(= 운영에선 `AES_SECRET` 환경변수). 이 마스터 키 하나만
안전하게 주입하면, 나머지 시크릿들은 yaml 에 `ENC(...)` 로 둘 수 있다.

---

## 2. 왜 이 방식인가 (결정 근거)
| 후보 | 판단 |
|---|---|
| **커스텀 EPP + AesCryptoService (채택)** | 이미 `AesCryptoService.encrypt/decrypt`(AES-GCM, Base64, **인증태그**), `framework.crypto.aes-secret`, Boot4 EPP 패턴(observability)이 다 있음. 신규 의존성 0, Jackson 무관, 레포 규약 일치. |
| Jasypt (`jasypt-spring-boot-starter`) | 최신 4.0.x 지만 **버전번호가 Boot 버전과 무관**하고 Boot 4(모듈화 자동구성·JSpecify) 공식 지원 근거 불명확(2026-06). 자동구성 내부 의존 → 리스크. **보류**. |
| Spring Cloud Config `{cipher}` | config 서버 인프라 필요 → 현 단계 과함. 추후 옵션. |

> 핵심 자산 재확인: `AesCryptoService.encrypt(String)` = `AES/GCM/NoPadding`, 출력 `Base64(IV(12B)||ct+tag)`.
> GCM 이라 **키가 틀리면 복호화가 예외로 실패**(조용히 깨진 값이 통과하지 않음) — 설정 암호화에 이상적.

---

## 3. 설계 (구현 가이드)

### 3.1 복호화 진입점 — `EnvironmentPostProcessor`
- 신규 클래스: `com.company.framework.core.crypto.EncryptedPropertyEnvironmentPostProcessor`
  (이름은 `*EnvironmentPostProcessor` — archtest 네이밍 규칙과 무충돌. `*AutoConfiguration`/`*Properties` 아님.)
- `implements org.springframework.boot.EnvironmentPostProcessor, org.springframework.core.Ordered`
  - ⚠️ **Boot4 패키지**: `org.springframework.boot.EnvironmentPostProcessor` (구 `...boot.env.*` 아님 — observability 함정과 동일).
- `postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app)`:
  1. 마스터 키 해석: `env.getProperty("framework.crypto.aes-secret")` (yaml 의 `${AES_SECRET:...}` placeholder 가 이 시점에 해석됨). 비어있으면 복호화 비활성(평문만 가정)하거나 ENC 발견 시 명확히 실패.
  2. 토글: `framework.crypto.config-decryption.enabled`(기본 true). ENC() 가 없으면 어차피 무동작이라 on 이 안전. (3단 토글 관례상 의심되면 기본 false 로 두고 문서화 — **결정 필요**: 본 설계는 기본 true 권장.)
  3. `AesCryptoService master = new AesCryptoService(key)` (빈 아님 — EPP 는 너무 일러 빈 사용 불가, 직접 생성).
  4. `env.getPropertySources()` 순회 → 각 `EnumerablePropertySource` 를 **복호화 데코레이터로 감싸 replace**.

### 3.2 복호화는 "지연(lazy) 래퍼"로
이른 시점 일괄 치환은 프로파일별 yaml 이 아직 안 들어왔을 때 누락될 수 있다. 값을 읽을 때 복호화하는 래퍼가 안전:
```java
final class DecryptingPropertySource extends PropertySource<PropertySource<?>> {
    private final AesCryptoService aes;
    DecryptingPropertySource(PropertySource<?> delegate, AesCryptoService aes) { super(delegate.getName(), delegate); this.aes = aes; }
    @Override public Object getProperty(String name) {
        Object v = getSource().getProperty(name);
        if (v instanceof String s && s.startsWith("ENC(") && s.endsWith(")")) {
            return aes.decrypt(s.substring(4, s.length() - 1));   // 실패 시 IllegalStateException → 기동 실패(의도)
        }
        return v;
    }
}
```
- `EnumerablePropertySource` 는 `getPropertyNames()` 도 위임해야 바인딩이 됨 → enumerable 용 별도 래퍼(또는 `getPropertyNames()` 오버라이드) 필요.
- 순서: config-data 임포트 후 실행되도록 `getOrder()` = `ConfigDataEnvironmentPostProcessor.ORDER + 1` 근처(또는 `Ordered.LOWEST_PRECEDENCE`). 지연 래퍼라도 source 가 존재해야 감싸므로 config-data 이후가 안전.

### 3.3 등록 — `META-INF/spring.factories`
```
org.springframework.boot.EnvironmentPostProcessor=\
  com.company.framework.core.crypto.EncryptedPropertyEnvironmentPostProcessor
```
⚠️ **키 한 글자만 틀려도 조용히 미등록**(복호화 안 됨). observability EPP 와 같은 함정.
(framework-core 에 이미 `spring.factories` 가 있으면 줄 추가, 없으면 신설.)

### 3.4 암호문 생성 도구 (개발자가 ENC 토큰 만들기)
빈/엔드포인트 말고 **CLI** 가 안전(암호화 엔드포인트 노출 금지). 택1:
- 작은 `main`: `com.company.framework.core.crypto.CryptoCli` — `encrypt <평문>` → `ENC(...)` 출력. 키는 `AES_SECRET` env.
- 또는 루트 `build.gradle` 에 `JavaExec` 태스크 `encryptSecret`.
사용 예(개발자가 1회):
```bash
AES_SECRET=... java -cp build/... com.company.framework.core.crypto.CryptoCli encrypt 'sipass'
# → ENC(Qk9k...) 를 yaml 에 붙여넣기
```
GCM 은 IV 랜덤이라 같은 평문도 매번 다른 암호문(정상).

---

## 4. 함정 (미리 박아둠)
- **마스터 키 자체는 `ENC(...)` 불가**(닭-달걀). `framework.crypto.aes-secret`/`AES_SECRET` 는 **항상 평문 주입**(env/시크릿). yaml 에 두지 말 것.
- **Boot4 EPP 패키지/`spring.factories` 키** 둘 다 정확히(`org.springframework.boot.EnvironmentPostProcessor`). 틀리면 무동작.
- **EPP 는 빈을 못 쓴다**(컨텍스트 전). `CryptoHolder`/`CryptoAutoConfiguration` 빈에 의존하지 말고 `AesCryptoService` 직접 생성.
- **복호화 실패=기동 실패가 정답**(GCM 인증). 예외를 삼켜 평문/깨진 값으로 진행 금지. 단 **에러 메시지에 평문/키 노출 금지**.
- **로그에 복호화값 출력 금지**(래퍼 toString/디버그 로깅 주의).
- **EnumerablePropertySource 의 `getPropertyNames()` 위임 누락**하면 바인딩이 ENC 값을 못 봄 → 반드시 enumerable 래퍼.
- **운영(prod) 정책 충돌 주의**: 현재 `application-prod.yml` 은 `${DB_PASSWORD}` 등 **env 주입**(기본값 없음). 같은 값을 yaml 에 `ENC(...)` 로도 두면 **이중**이 된다. 정책 택1: (a) prod 는 env 주입 유지(ENC 는 local/dev 편의), (b) prod 도 ENC 로 통일하고 마스터키만 env. **권장: (a)** — 운영 비밀은 시크릿 매니저/Env, ENC 는 dev 편의 + 저장소에 평문 안 남기기 용도.
- **JWT 가드와의 순서**: `JwtSecretSafetyGuard`(빈, 컨텍스트 init 시점)는 EPP 복호화 **이후** 값을 본다 → `jwt.secret: ENC(...)` 도 정상 동작(복호화된 강한 키를 가드가 검사). 단 가드의 "change-this/change-me" 흔적 검사는 복호화 후 평문 기준.
- **Jackson 금지 규약**: 이 작업은 Jackson 안 씀(순수 crypto + Spring property). `tools.jackson.*`/`com.fasterxml.*` 등장할 일 없음.
- **spotless/설정캐시**: 직전 세션에서 `lineEndings = UNIX` 로 고정·해결됨. 새 파일은 그 규약대로(UTF-8/LF).

---

## 5. 테스트 계획
- `AesCryptoService` round-trip 은 이미 검증 가능(encrypt→decrypt).
- EPP 단위: `StandardEnvironment` + `MapPropertySource({"a":"ENC(...)","b":"plain"})` 에 EPP 적용 →
  - `a` 는 복호화 평문, `b` 는 그대로.
  - 잘못된 키/조작된 암호문 → 예외.
  - 마스터 키 미설정 + ENC 존재 → 명확한 실패(또는 정책상 skip).
- 통합(선택): `SpringApplicationBuilder` 로 `ENC()` 가 든 프로퍼티를 띄워 빈 주입까지 확인.
- archtest: framework-core 내부라 **새 모듈 아님** → `framework-archtest/build.gradle` 변경 불필요. 네이밍 규칙만 준수.

---

## 6. 후속(이번 작업에 곁들이면 좋은 것)
- **AES 마스터 키 prod 가드**: JWT 가드(`JwtSecretSafetyGuard`)와 동일 패턴으로 `framework.crypto.aes-secret` 가
  prod 에서 기본값(`change-me-please-set-framework-crypto-aes-secret`)이면 부팅 실패시키는 가드 추가. (설정 암호화의 신뢰 기반이 마스터 키이므로 강하게.)
- **키 회전(rotation)**: 마스터 키 교체 절차(구키 복호→신키 재암호) 문서/스크립트. (백로그)

---

## 7. 착수 체크리스트 (다음 세션 그대로 실행)
1. `framework-core` 에 `EncryptedPropertyEnvironmentPostProcessor` + (enumerable) `DecryptingPropertySource` 추가.
2. `META-INF/spring.factories` 에 EPP 등록(키 정확히).
3. 토글 프로퍼티 `framework.crypto.config-decryption.enabled`(+ `CryptoProperties` 에 필드, 기본값 결정).
4. `CryptoCli`(encrypt) 또는 gradle `encryptSecret` 태스크.
5. 테스트(EPP 단위 + round-trip + 실패 케이스).
6. (선택) AES 마스터키 prod 가드.
7. 문서 동기화: `STACK.md`(암호화 방식 1줄)·`README.md`(ENC 사용법)·`docs/FRAMEWORK_MODULES.md`(crypto 책임 확장)·`docs/BASELINE_FEATURES.md`(항목 ✅)·`HANDOFF.md`(§6 함정·§7 완료)·`HANDOFF_SUMMARY.md`.
8. 검증: `./gradlew :framework:framework-core:test :framework:framework-archtest:test spotlessApply` + 한 서비스에서 `ENC()` 실제 기동 확인.
