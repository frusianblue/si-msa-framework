# framework-oauth-client 드롭인 안내

## 1) 압축 풀기
`framework/framework-oauth-client/` 를 프로젝트 루트의 `framework/` 아래에 그대로 복사.
`docs/modules/OAUTH_CLIENT.md` 는 사용 가이드(각 프로젝트에서 참고).

## 2) 기존 파일 2곳에 한 줄씩 추가 (이 두 줄만 직접 넣으면 됩니다)

settings.gradle — framework-mfa 줄 아래:
```gradle
include 'framework:framework-oauth-client'  // 선택형: 소셜 로그인(OAuth2/OIDC → 자체 JWT 발급)
```

framework/framework-archtest/build.gradle — dependencies 블록 안, framework-mfa 줄 아래:
```gradle
    testImplementation project(':framework:framework-oauth-client')
```

## 3) 검증 (받는 환경에서)
```bash
./gradlew :framework:framework-oauth-client:test
./gradlew :framework:framework-archtest:test
./gradlew spotlessApply
```
(작성 환경은 Maven Central 접근이 막혀 Spring 풀 컴파일을 돌리지 못했습니다. 위 3개만 그린이면 완료.)
