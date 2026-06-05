# WebAuthn rpId/origin 멀티서비스 일원화 정책

> 적용: `framework-webauthn`(1차 passwordless) · `framework-mfa` WebAuthn 2차(독립 등록형). 두 기능 모두 동일 RP 연산 빈을 쓰므로 rpId/origin 설정이 자동 일관된다.

## 1. 왜 일원화가 필요한가

WebAuthn 자격증명(패스키)은 **rpId(Relying Party ID = 등록 가능 도메인)** 에 바인딩된다. 등록 시 rpId 로 저장되고, 검증 시 **같은 rpId** 여야 그 패스키를 쓸 수 있다. 또 ceremony 를 수행하는 페이지의 **origin** 은 rpId 와 같거나 그 **하위 도메인**이어야 하며(아니면 브라우저/RP 가 거부), HTTPS(SecureContext)에서만 동작한다(localhost 예외).

MSA 에서는 서비스(gateway/auth-server/user-service/admin-service…)가 각자 설정을 들고 배포되므로, **서비스마다 rpId/origin 이 어긋나면** 한 서비스에서 등록한 패스키를 다른 서비스가 검증하지 못한다(ceremony 거부). 그래서 rpId/origin 을 **조직 단위로 일원화**한다.

## 2. 핵심 결정: rpId 의 범위

| 선택 | rpId 예시 | 효과 | 적합 |
|---|---|---|---|
| **공통 상위 도메인(권장)** | `example.com` | `*.example.com` 전 서비스가 **같은 패스키 공유**(1회 등록 → 전 서비스 인증). credential 저장소 공유 시 SSO 형 패스키 | 대부분의 사내/금융 MSA |
| 서비스별 호스트 | `user.example.com` | 서비스별 패스키 **격리**(서비스마다 별도 등록) | 서비스 간 신뢰 경계가 엄격한 경우(드묾) |

> **권장: 공통 상위 등록가능 도메인 단일값.** 전 서비스 `framework.webauthn.rp-id` 를 동일하게(예: `example.com`) 둔다. 패스키 UX(사용자당 1회 등록)와 credential 저장소 공유에 가장 정합적이다.

origin(`allowed-origins`)은 **프런트엔드 공개 origin(들)** — 게이트웨이/Ingress 가 노출하는 호스트다. 전 서비스가 **동일한 목록**을 공유한다. 각 origin host 는 rpId 의 하위 도메인이어야 한다(예: rpId `example.com` ↔ origin `https://app.example.com`).

## 3. 권장 토폴로지

```
[브라우저: https://app.example.com]  ← origin (allowed-origins)
        │  navigator.credentials.{create,get}
        ▼
[Ingress/TLS] → [gateway] → ceremony 전담 서비스(auth-server)
                                rp-id: example.com
                                allowed-origins: [https://app.example.com]
                                credential store: jdbc(공용 인증 DB)
```

- **ceremony(등록/검증)는 한 서비스가 전담**(보통 auth-server 또는 전용 webauthn 서비스). 여러 서비스가 각자 ceremony 를 열면 challenge 상태(티켓/세션) 분산과 rpId 관리 부담이 커진다. 게이트웨이가 `/api/*/auth/webauthn/**`·`{credentials-path}` 를 그 서비스로 라우팅한다.
- **credential 저장소(jdbc)는 공유**(공용 인증 DB 또는 ceremony 전담 서비스 단일 소유). rpId 가 같아도 저장소가 분리되면 서비스 간 검증이 안 된다.
- **MFA WebAuthn 2차도 같은 RP 연산/저장소를 재사용**하므로, 등록을 auth-server 가 했고 2차 검증을 다른 서비스가 하더라도 rpId 동일 + 저장소 공유면 정합한다.

## 4. 설정 일원화 방법

서비스별 `application.yml` 에 rp-id/allowed-origins 를 **중복 하드코딩하지 않는다.** 단일 출처에서 주입한다.

- **Spring Cloud Config / 공유 yml**: 공통 `application-webauthn.yml`(또는 Config Server 의 공통 프로파일)에 정의하고 각 서비스가 import.
- **Kubernetes**: 공용 `ConfigMap`(예: `webauthn-rp`)을 전 서비스 Deployment 가 `envFrom`/volume 으로 공유.

```yaml
# 단일 출처(공통). 전 서비스가 이 값을 그대로 받는다.
framework:
  webauthn:
    enabled: true
    rp-id: ${WEBAUTHN_RP_ID:example.com}
    rp-name: "SI-MSA"
    allowed-origins:
      - ${WEBAUTHN_ORIGIN:https://app.example.com}
```

```yaml
# k8s ConfigMap 예시
apiVersion: v1
kind: ConfigMap
metadata: { name: webauthn-rp }
data:
  WEBAUTHN_RP_ID: "example.com"
  WEBAUTHN_ORIGIN: "https://app.example.com"
```

## 5. 기동 검증 가드(자동 강제)

`WebAuthnRpSafetyGuard` 가 **서비스마다 기동 시** rpId/origin 정합을 검사한다(jwt-secret/session-store 가드와 동일 패턴). 미스컨피그를 운영 전에 잡는다.

| 조건 | prod | 비-prod |
|---|---|---|
| rp-id 비어 있음 | 부팅 실패 | 경고 |
| prod 에서 rp-id 가 `localhost` | 부팅 실패 | — |
| allowed-origins 비어 있음 | 부팅 실패 | 허용(단일 서비스 DSL 추론) |
| origin 이 https 아님(localhost 예외) | 부팅 실패 | 경고 |
| origin host 가 rp-id 의 등록가능 도메인 밖 | 부팅 실패 | 경고 |

> 멀티서비스에서 한 서비스만 rp-id 를 다르게 주입해도 그 파드가 기동에서 멈춘다(또는 경고). "전 서비스 동일 설정"을 운영 차원에서 보증하는 안전망이다.

## 6. 안티패턴

- ❌ 서비스마다 rp-id 를 자기 호스트로(`user.example.com`, `admin.example.com`) → 패스키가 서비스별로 갈려 재사용 불가(의도한 격리가 아니라면 실수).
- ❌ credential 저장소를 서비스별 분리 DB 로 → rpId 가 같아도 등록/검증 서비스가 다르면 자격증명을 못 찾음.
- ❌ allowed-origins 에 rp-id 와 무관한 호스트 → ceremony 거부(가드가 차단).
- ❌ prod 에서 http origin / rp-id=localhost → SecureContext 위반(가드가 차단).
- ❌ rp-id 를 `com` 같은 public suffix 나 IP 로 → 브라우저가 거부(등록가능 도메인 아님).

## 7. 체크리스트(배포 전)

- [ ] 전 서비스 `framework.webauthn.rp-id` 동일(공통 상위 도메인) — 단일 출처 주입.
- [ ] 전 서비스 `allowed-origins` 동일(프런트 공개 origin), 각 host 가 rp-id 하위 도메인.
- [ ] prod 는 모든 origin https.
- [ ] credential 저장소(jdbc) 공유(공용 인증 DB 또는 ceremony 전담 서비스).
- [ ] ceremony 경로(`/api/*/auth/webauthn/**`, `{credentials-path}`)가 게이트웨이에서 ceremony 전담 서비스로 라우팅.
- [ ] MFA WebAuthn 2차(`framework.mfa.webauthn.enabled`)도 같은 rp-id/저장소 컨텍스트 위에서 동작 확인.
- [ ] 각 서비스 기동 로그에 `WebAuthnRpSafetyGuard` 경고/실패 없음.

---
관련: [`framework-webauthn/README.md`](../../framework/framework-webauthn/README.md) · [`framework-mfa/README.md`](../../framework/framework-mfa/README.md) · [`AUTH_COMPOSITION_GUIDE.md`](AUTH_COMPOSITION_GUIDE.md) · 함정 [`PITFALLS.md`](PITFALLS.md)
