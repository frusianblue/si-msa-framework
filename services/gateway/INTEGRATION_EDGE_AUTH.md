# 게이트웨이 엣지 인증 — 드롭인 안내

## 새/수정 파일
- 새 파일:
  - src/main/java/com/company/gateway/config/GatewayAuthProperties.java
  - src/main/java/com/company/gateway/config/GatewayAuthConfiguration.java
  - src/main/java/com/company/gateway/auth/GatewayTokenVerifier.java
  - src/main/java/com/company/gateway/auth/GatewayAuthGlobalFilter.java
  - src/test/java/com/company/gateway/auth/GatewayTokenVerifierTest.java
- 수정 파일(이 zip 으로 덮어쓰기):
  - build.gradle               (jjwt 의존 3줄 추가)
  - src/main/resources/application.yml   (gateway.auth 블록 추가)
  - src/main/java/com/company/gateway/config/RateLimitConfiguration.java  (검증 userId 우선)

## 검증
./gradlew :services:gateway:compileJava :services:gateway:test

## 켜기(운영)
환경변수: GATEWAY_AUTH_ENABLED=true, JWT_SECRET=<security 와 동일 키>
