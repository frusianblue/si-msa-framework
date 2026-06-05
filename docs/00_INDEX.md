# 문서 지도 (Documentation Index)

> 이 한 장이 모든 문서의 진입점이다. **무엇을 하려는지**로 골라서 들어간다.
> 스택/버전은 [`reference/STACK.md`](reference/STACK.md), 모듈 설계 원본은 [`FRAMEWORK_MODULES.md`](FRAMEWORK_MODULES.md).

---

## 나는 누구인가? — 역할별 시작점

### ⓐ 이 프레임워크를 **받아서 업무를 개발하는** 사람
1. [`guide/GETTING_STARTED.md`](guide/GETTING_STARTED.md) — 5분 안에 띄우기
2. [`guide/MODULE_COMPOSITION.md`](guide/MODULE_COMPOSITION.md) — **내 프로젝트에 어떤 모듈을 켤지** (기능·전제·연결·토글 한눈에)
3. [`guide/USAGE_BY_PROJECT_TYPE.md`](guide/USAGE_BY_PROJECT_TYPE.md) — 금융/공공/일반 프리셋
4. [`guide/DEVELOPER_GUIDE.md`](guide/DEVELOPER_GUIDE.md) — 표준 응답/예외/인증/감사 등 "이렇게 쓰세요"
5. [`guide/SAMPLES.md`](guide/SAMPLES.md) — 복붙 가능한 샘플 코드

### ⓑ 프레임워크를 **조립·확장·유지보수하는** 사람 (오너)
1. [`FRAMEWORK_MODULES.md`](FRAMEWORK_MODULES.md) — 전 모듈 카탈로그·토글 규약·의존 관계·구축 순서
2. [`guide/AUTH_COMPOSITION_GUIDE.md`](guide/AUTH_COMPOSITION_GUIDE.md) — **무엇을 골라 조합하나**: 인증/상태/소셜/MFA/SSO 결정 + 표준 레시피
3. [`guide/JWT_STATELESS_PITFALLS.md`](guide/JWT_STATELESS_PITFALLS.md) — **JWT·무상태 함정 사례집 + 확장 가이드**(실제 겪은 문제·원인·해결·교훈)
4. [`guide/PITFALLS.md`](guide/PITFALLS.md) — **함정·교훈 대장**(전 영역 누적 자산: 빌드/Jackson/오토컨피그/보안/DB/테스트/환경). 새 함정은 여기 계속 쌓는다
5. [`reference/STACK.md`](reference/STACK.md) — 버전 단일 소스(Boot 4.0.6 / Java 21 / SC 2025.1.1 / Jackson 3)
6. [`reference/CHANGES_AND_DEPRECATIONS.md`](reference/CHANGES_AND_DEPRECATIONS.md) — 버전업 시 깨지는 지점
7. 모듈 내부 동작 → 각 `framework/framework-*/README.md`

### ⓒ **운영·배포(DevOps)** 하는 사람
1. [`ops/LOCAL_SETUP.md`](ops/LOCAL_SETUP.md) — 로컬 DB/Redis 기동
2. [`ops/LOCAL_K8S_ENV_SETUP.md`](ops/LOCAL_K8S_ENV_SETUP.md) · [`ops/LOCAL_K8S_TEST.md`](ops/LOCAL_K8S_TEST.md) — kind 로 로컬 클러스터 검증
3. [`ops/K8S_ADDONS.md`](ops/K8S_ADDONS.md) — 클러스터 애드온(metrics-server·Prometheus·Ingress·Secret 오퍼레이터)
4. [`ops/K8S_CICD_MULTISERVICE.md`](ops/K8S_CICD_MULTISERVICE.md) — Kustomize base/overlay·4서비스 CI/CD
5. [`ops/SONARQUBE_GUIDE.md`](ops/SONARQUBE_GUIDE.md) — 코드 품질·보안약점 게이트(SonarQube) 사용법(서버 기동·토큰·분석 실행·Quality Gate)

---

## 주제별 색인

| 주제 | 문서 |
|---|---|
| 전체 모듈 카탈로그·의존·구축순서 | [`FRAMEWORK_MODULES.md`](FRAMEWORK_MODULES.md) |
| 모듈 조합(내 프레임워크 만들기) | [`guide/MODULE_COMPOSITION.md`](guide/MODULE_COMPOSITION.md) |
| 인증/상태/소셜/MFA/SSO 결정·레시피 | [`guide/AUTH_COMPOSITION_GUIDE.md`](guide/AUTH_COMPOSITION_GUIDE.md) |
| JWT·무상태 함정·확장 가이드 | [`guide/JWT_STATELESS_PITFALLS.md`](guide/JWT_STATELESS_PITFALLS.md) |
| 함정·교훈 대장(전 영역 누적) | [`guide/PITFALLS.md`](guide/PITFALLS.md) |
| 스택/버전 핀 | [`reference/STACK.md`](reference/STACK.md) |
| 기본 제공 기능 목록 | [`reference/BASELINE_FEATURES.md`](reference/BASELINE_FEATURES.md) |
| 설정값/필드 암호화(`ENC(...)`) | [`reference/ENCRYPTION_GUIDE.md`](reference/ENCRYPTION_GUIDE.md) |
| 토큰 검증(JWT/OIDC) | [`reference/TOKEN_VERIFICATION_GUIDE.md`](reference/TOKEN_VERIFICATION_GUIDE.md) |
| 입력값 검증 보강 | [`reference/SECURITY_VALIDATION_ADDITIONS.md`](reference/SECURITY_VALIDATION_ADDITIONS.md) |
| 인가서버(OP) | [`modules/AUTH_SERVER.md`](modules/AUTH_SERVER.md) |
| 소셜 로그인/OIDC RP | [`modules/OAUTH_CLIENT.md`](modules/OAUTH_CLIENT.md) · [`modules/OIDC_HARDENING.md`](modules/OIDC_HARDENING.md) |
| SAML 2.0 SP | [`modules/SAML_SP.md`](modules/SAML_SP.md) |
| 게이트웨이 엣지 인증 | [`modules/GATEWAY_EDGE_AUTH.md`](modules/GATEWAY_EDGE_AUTH.md) · [`modules/GATEWAY_RUNTIME_CHECK.md`](modules/GATEWAY_RUNTIME_CHECK.md) |
| SSO 중앙 로그아웃 | [`modules/SSO_CENTRAL_LOGOUT.md`](modules/SSO_CENTRAL_LOGOUT.md) |

---

## 서비스 (실행 단위)

| 서비스 | 포트 | README |
|---|---|---|
| gateway | 8000 | [`services/gateway/README.md`](../services/gateway/README.md) |
| auth-server (OP) | 9000 | [`services/auth-server/README.md`](../services/auth-server/README.md) |
| user-service (샘플) | 8080 | [`services/user-service/README.md`](../services/user-service/README.md) |
| admin-service | 8081 | [`services/admin-service/README.md`](../services/admin-service/README.md) |

---

## 작업 기록 (개발자는 볼 필요 없음)

세션 인수인계·적용 노트는 [`_internal/`](_internal/)로 분리. 프레임워크 사용과 무관.
