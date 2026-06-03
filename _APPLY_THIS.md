# 적용 안내 (이 zip)

저장소 루트(`si-msa-framework/`)에서:
```bash
unzip -o si-msa-local-env-bundle.zip
```
하면 아래 파일들이 제자리에 떨어진다. **기존 파일을 덮어쓴다** — git diff 로 변경 확인 후 커밋 권장.

## 포함 파일
```
services/user-service/src/main/resources/
  application.yml                  (개편: 공통만, DB는 프로파일로)
  application-local.yml            (H2 메모리 + H2콘솔 + audit jdbc)
  application-local-postgres.yml   (신규: 로컬 PG 오버레이)
  application-local-redis.yml      (신규: Redis 오버레이)
  application-local-noauth.yml     (신규: 옛 dev 로그인우회 이전)
  application-dev.yml              (개편: 개발 서버 의미로)
  application-prod.yml             (신규: 운영)
  db/migration/V4__audit_log.sql   (신규: 감사 로그 테이블)

services/admin-service/src/main/resources/
  application.yml / application-local*.yml / application-dev.yml / application-prod.yml  (동일 체계)
  db/migration/V2__audit_log.sql   (신규)

docs/
  LOCAL_SETUP.md                   (설치 목록·설정·로그 DB 검증)
  CHANGES_AND_DEPRECATIONS.md      (deprecated/변경 통합 정리)
  SECURITY_VALIDATION_ADDITIONS.md (JWT 운영 가드 + 요청 검증 보강 설명)

framework/framework-security/.../jwt/JwtSecretSafetyGuard.java        (신규: prod JWT 시크릿 가드)
framework/framework-security/.../jwt/JwtSecretSafetyGuardTest.java    (신규: 테스트)
framework/framework-security/.../config/SecurityAutoConfiguration.java (수정: 가드 빈 등록)
framework/framework-security/.../auth/AuthController.java             (수정: 로그인 @Valid)
framework/framework-security/.../auth/LoginCommand.java               (수정: @NotBlank)
framework/framework-core/.../error/GlobalExceptionHandler.java        (수정: HandlerMethodValidationException 처리)
```

> 보안/검증 변경(JWT 가드·validation)은 **새 의존성이 필요 없다** — validation 은 이미 framework-core 가 전이 노출.
> 아래 build.gradle 추가는 "감사 DB 적재"·"Redis 기능"에만 해당.

## 코드(build.gradle)에서 추가로 해야 할 일 — zip 에 없음(직접 1줄씩 추가, IntelliJ 재임포트)
1. **감사 로그 DB 적재**: user/admin `build.gradle` dependencies 에
   ```gradle
   implementation project(':framework:framework-audit')
   ```
2. **Redis 기능**: (local-redis/dev/prod 사용 시)
   ```gradle
   implementation project(':framework:framework-redis')
   implementation project(':framework:framework-cache-redis')
   implementation 'org.springframework.boot:spring-boot-starter-data-redis'
   ```

## 빠른 확인
```bash
# 메모리 DB 로 기동 → /h2-console 에서 audit_log 확인
./gradlew :services:user-service:bootRun
# 로컬 PostgreSQL 로
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'
```
검증 상세: docs/LOCAL_SETUP.md §5.
