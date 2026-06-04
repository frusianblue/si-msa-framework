# 사업유형별 프리셋 (Usage by Project Type)

> 같은 코어 + yml 프리셋 교체만으로 사업유형을 전환한다. 모듈별 의미는 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md).
> 미설정 토글은 기본 `false`(꺼짐)다 — 아래는 **켜는 것만** 적는다.

## 금융 (full set) — `application-finance.yml`
```yaml
framework:
  idempotency: { enabled: true,  store: { type: jdbc }, replay: { enabled: true } }   # 중복요청/결제 차단 + 응답 재생
  messaging:   { enabled: true,  outbox: { relay: { enabled: true } } }               # 신뢰성 발행(Outbox)
  saga:        { enabled: true,  recovery: { enabled: true } }                        # 분산 트랜잭션 보상(messaging 전제)
  datasource:  { routing: { enabled: true } }                                          # 읽기/쓰기 분리 (또는 multi — 배타)
  audit:       { enabled: true,  store: { type: jdbc } }                               # 감사로그 DB 적재
  mfa:         { enabled: true }                                                        # 2단계 인증
  pki:         { enabled: true }                                                        # (예정) 전자서명/부인방지
  security:
    password: { expiry: { enabled: true }, history: { enabled: true } }
    concurrent-session: { enabled: true }
```
> 연계 3종(idempotency→messaging→saga)은 함께 배선. 멀티 인스턴스면 store 는 `jdbc`/`redis`.

## 공공 — `application-public.yml`
```yaml
framework:
  egov:       { enabled: true }                # (예정) 전자정부 표준프레임워크 호환
  pki:        { enabled: true }                # (예정) GPKI/NPKI
  audit:      { enabled: true, store: { type: jdbc } }
  secure-web: { enabled: true }
  # SSO 필요 시: saml-sp.enabled=true + 등록(IdP 메타데이터) — docs/modules/SAML_SP.md
```

## 일반 기업 (라이트) — `application-enterprise.yml`
```yaml
framework:
  idempotency: { enabled: true, store: { type: redis } }   # 중복요청 방지는 보편적
  audit:       { enabled: true, store: { type: jdbc } }
  cache:       { redis: { enabled: true } }                # 다중 파드면 분산 캐시
  # 규제 특화(egov/pki/mfa/hsm/recon/saga) 미설정 → 꺼짐
  # B2C 소셜 로그인 필요 시: oauth-client.enabled=true — docs/modules/OAUTH_CLIENT.md
```

## 공통 바닥 (모든 유형)
core · mybatis · security · i18n · idgen · secure-web · observability 는 어느 유형이든 켠다.
레퍼런스 구현(3단 토글 예)은 `framework/framework-idempotency/` 참고.
