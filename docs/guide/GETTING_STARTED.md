# 5분 시작 (Getting Started)

> 받은 프레임워크를 가장 빠르게 띄워 보는 길. 모듈 선택은 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md), 사업유형 프리셋은 [`USAGE_BY_PROJECT_TYPE.md`](USAGE_BY_PROJECT_TYPE.md).

## 사전 준비
JDK 21 만 있으면 된다. 로컬은 H2 인메모리 + Flyway 자동 시드 → **외부 DB·Redis 불필요**.
(Windows 툴체인은 [`../ops/DEV_ENV_WINDOWS.md`](../ops/DEV_ENV_WINDOWS.md).)

## 1. 빌드
```bash
./gradlew spotlessApply      # 최초 1회 포맷 정렬
./gradlew clean build        # 컴파일+테스트 (테스트 생략: build -x test)
```

## 2. 기동
```bash
./gradlew :services:user-service:bootRun    # → http://localhost:8080  (프로파일 local, H2)
```

## 3. 동작 확인
시드 계정: `admin/admin123`(ADMIN), `hong/hong123`(USER)
```bash
curl -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"admin","password":"admin123"}'
```
- 같은 `loginId` 로 5회 실패 → 6번째부터 `429 LOGIN_LOCKED`
- 회원가입 시 비밀번호 강도(길이 9·문자 3종 이상) 미달이면 `400`

## 4. 다음
- 어떤 모듈을 켤지 결정 → [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md)
- 사업유형(금융/공공/일반) 일괄 설정 → [`USAGE_BY_PROJECT_TYPE.md`](USAGE_BY_PROJECT_TYPE.md)
- 표준 응답/예외/인증을 코드에서 쓰는 법 → [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md)
- 로컬 PostgreSQL/Redis 로 전환 → [`../ops/LOCAL_SETUP.md`](../ops/LOCAL_SETUP.md)
