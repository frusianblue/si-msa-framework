# LOCAL_SETUP.md — 로컬 개발 환경 구축 / 설치 목록 / 검증

> 대상: `si-msa-framework` 로컬 개발. 인프라를 **로컬에 직접 설치**해서 눈으로 확인하는 워크플로 기준.
> Docker 로 한 방에 띄우는 대안은 맨 끝 부록 참고.
> **4서비스(gateway/auth-server/user-service/admin-service)까지 통째로** 컨테이너로 띄워 통합 검증하려면
> [`../../deploy/compose/README.md`](../../deploy/compose/README.md)(소스부터 빌드하는 Docker Compose 스택)를 쓴다.

---

## 0. 환경(프로파일) 규약 — local / dev / prod 로 통일

| 프로파일 | 용도 | DB | Redis | 비고 |
|---|---|---|---|---|
| **local** | 로컬 개발 기본 | H2 인메모리 | off | 외부 설치 0, 가장 빠름 |
| **dev** | 개발 서버 | PostgreSQL(원격) | on | 환경변수 주입 |
| **prod** | 운영 | PostgreSQL(시크릿) | on | 비밀 미주입 시 기동 실패 |

특수/조합은 **`local-xx` 오버레이**로 `local` 위에 겹쳐 쓴다(서로 자유롭게 조합).

| 오버레이 | 효과 |
|---|---|
| **local-postgres** | DB 를 H2 → 로컬 설치 PostgreSQL 로 교체 |
| **local-redis** | Redis 기능(분산 캐시·토큰스토어·로그인시도) on |
| **local-noauth** | 로그인/권한 우회 (예전 `dev` 프로파일이 하던 역할) |

```bash
# 메모리 DB (설치 0)
./gradlew :services:user-service:bootRun
# 로컬 PostgreSQL
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'
# PostgreSQL + Redis
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres,local-redis'
# 로그인 우회까지
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres,local-noauth'
```

> ⚠️ 과거 `--spring.profiles.active=local,dev` (로그인 우회) → 이제 **`local,local-noauth`** 로 바꾼다.
> `dev` 는 이제 "개발 서버 환경"을 의미한다. (자세한 내용 `docs/CHANGES_AND_DEPRECATIONS.md`)

---

## 1. 로컬 설치 목록

| # | 도구 | 권장 버전(2026-06) | 활성화되는 기능 | 필수/선택 |
|---|---|---|---|---|
| 1 | **JDK (Temurin) 21** | 21 (LTS) | 빌드/실행 전체 | **필수** |
| 2 | **PostgreSQL** | 17.x 또는 18.x | `local-postgres`, `dev`, `prod` DB · 감사로그 영속 | **필수(메모리만 쓸 땐 선택)** |
| 3 | **Redis** | 8.x (또는 Valkey 8.x) | `local-redis`/`dev`/`prod`, 게이트웨이 rate-limit | 선택(분산 기능 검증 시) |
| 4 | ClamAV | 1.x | `framework.file.scan`(#10 안티바이러스 게이트) | 선택 |
| 5 | MinIO | 최신 | `framework-file-s3`(S3 호환, presigned) 로컬 검증 | 선택 |
| 6 | Kafka(또는 Redpanda) | 3.x / 최신 | `framework-messaging`, `audit.store.type=kafka` | 선택 |

> H2·JDBC 드라이버·Flyway·datasource-proxy 는 Gradle 의존으로 이미 포함 — 별도 설치 불필요.
> JDK 만 있으면 `local`(메모리) 로 즉시 개발 가능. 2·3 은 "직접 확인" 단계에서 설치한다.

---

## 2. PostgreSQL 설치 + DB/계정 생성

서비스 기본값과 맞추기 위해 **DB=`userdb`, 사용자=`user_app`/`dev-userpass`** 로 만든다.
(admin-service 도 같은 `userdb` 를 공유한다 — 테이블이 겹치지 않음.)

### 설치
- **Windows**: [postgresql.org/download/windows](https://www.postgresql.org/download/windows/) 의 EDB 인스톨러. 설치 중 superuser(`postgres`) 비밀번호 지정. `psql` 은 시작메뉴 "SQL Shell (psql)".
- **macOS**: `brew install postgresql@17 && brew services start postgresql@17`
- **Ubuntu/WSL**: `sudo apt update && sudo apt install -y postgresql && sudo service postgresql start`

### DB/계정 만들기 (psql 접속 후)
```sql
CREATE DATABASE userdb;
CREATE USER user_app WITH PASSWORD 'dev-userpass';
GRANT ALL PRIVILEGES ON DATABASE userdb TO user_app;
-- PostgreSQL 15+ 는 public 스키마 권한을 따로 줘야 한다
\connect userdb
GRANT ALL ON SCHEMA public TO user_app;
```
> Windows EDB 설치본은 `psql -U postgres` 로 접속(설치 시 정한 비번). mac/Linux 는 `sudo -u postgres psql`.

### 연결 확인
```bash
psql "host=localhost port=5432 dbname=userdb user=user_app password=dev-userpass" -c "select version();"
```

테이블은 앱 기동 시 **Flyway 가 자동 생성**(`db/migration` + 감사 `V4/V2__audit_log.sql`). 수동 DDL 불필요.

---

## 3. Redis 설치

> ⚠️ 라이선스: Redis 8.0+ 는 AGPLv3 / RSALv2 / SSPLv1 트라이라이선스다. 운영 배포 라이선스가 걸리면
> BSD 포크인 **Valkey**(명령 호환) 사용을 검토. 로컬 개발 검증엔 어느 쪽이든 무방.

### 설치/기동
- **Windows**: 네이티브 공식 지원이 없다. 택1
  - **WSL2(권장)**: `wsl --install` 후 Ubuntu 에서 아래 Ubuntu 방법대로. (`localhost:6379` 로 접근됨)
  - **Memurai**: Windows 용 Redis 호환 서비스(개발용 무료 티어). 설치하면 서비스로 상주.
  - **Docker**: `docker run -d --name redis -p 6379:6379 redis:8`
- **macOS**: `brew install redis && brew services start redis`
- **Ubuntu/WSL**: `sudo apt install -y redis-server && sudo service redis-server start`

### 확인
```bash
redis-cli ping        # → PONG
```

### 앱에서 Redis 쓰기 — build.gradle 의존 추가 필요
`local-redis`/`dev`/`prod` 로 Redis 기능을 켜려면 서비스 `build.gradle` 의 `dependencies` 에 추가:
```gradle
// ===== Redis (분산 캐시 / 토큰스토어 / 로그인시도 카운터) =====
implementation project(':framework:framework-redis')        // 토큰스토어·로그인시도
implementation project(':framework:framework-cache-redis')  // 분산 캐시
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```
> 게이트웨이(`gateway`)는 이미 reactive Redis(rate-limit)를 쓰고 있어 별도 작업 없음. `REDIS_HOST`/`REDIS_PORT` 만 맞추면 된다.

---

## 4. (선택) 그 외 인프라

- **ClamAV** (`framework.file.scan.type=clamav`, 기본 포트 3310)
  - macOS: `brew install clamav` → `freshclam` 으로 시그니처 받고 `clamd` 기동
  - Ubuntu: `sudo apt install -y clamav clamav-daemon && sudo systemctl start clamav-daemon`
  - fail-closed 기본 — 데몬이 안 떠 있으면 업로드가 거부된다. 검증 안 할 땐 `scan.enabled=false` 유지.
- **MinIO** (S3 호환, `framework-file-s3` + `storage.type=s3`)
  - `docker run -d -p 9000:9000 -p 9001:9001 -e MINIO_ROOT_USER=minio -e MINIO_ROOT_PASSWORD=minio12345 minio/minio server /data --console-address ":9001"`
  - `framework.file.storage.s3.endpoint` 에 `http://localhost:9000`, region 아무거나, 버킷 사전 생성.
- **Kafka** (`audit.store.type=kafka`, `framework-messaging`): 로컬은 Redpanda 단일 노드 또는 Kafka KRaft 모드 docker 가 간편.

---

## 5. 로그가 DB 에 실제로 적재되는지 검증 ★

핵심 포인트: **감사 로그(audit_log)** 가 DB 에 쌓이려면 3가지가 모두 맞아야 한다.

1. **모듈 의존** — 서비스 `build.gradle` 에 감사 모듈 추가 (현재 미포함):
   ```gradle
   implementation project(':framework:framework-audit')
   ```
2. **설정** — `framework.audit.enabled=true` + `store.type=jdbc`
   (공통 `application.yml` 에서 enabled=true, `local`/`dev`/`prod` 가 store.type=jdbc 로 설정해 둠)
3. **테이블** — `audit_log` 가 존재 (이번에 추가한 `V4__audit_log.sql`/`V2__audit_log.sql` 을 Flyway 가 자동 적용)

> 셋 중 하나라도 빠지면 `JdbcAuditEventSink` 는 INSERT 실패를 **조용히 삼키고 WARN 로그만** 남긴다
> (비즈니스 흐름을 깨지 않으려는 설계). "로그가 안 쌓인다"의 대부분이 1번(모듈 미의존) 또는 3번(테이블 부재).

### 검증 절차 (메모리 H2 기준 — 가장 빠름)
```bash
# 1) 감사 모듈 의존 추가 후 기동
./gradlew :services:user-service:bootRun        # profiles.active=local (H2 + audit jdbc)
```
```
# 2) 감사 이벤트 유발 — 로그인 시도(성공/실패 무관, LOGIN_* 이벤트 적재)
curl -i -X POST http://localhost:8080/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"loginId":"admin","password":"admin123"}'
```
```
# 3) H2 콘솔로 눈으로 확인
#    브라우저: http://localhost:8080/h2-console
#    JDBC URL: jdbc:h2:mem:userdb   User: sa   Password: (빈칸)
SELECT * FROM audit_log ORDER BY event_time DESC;
```
- `@AuditLog` 가 붙은 메서드를 호출하면 `event_type=METHOD_AUDIT` 행도 생긴다.
- `store.type=jdbc` 일 때만 조회 API(`AuditController`, `GET /api/v1/audit/logs`)도 함께 활성화된다 → API 로도 확인 가능.

### PostgreSQL 에서 확인
```bash
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'
# 위 2) 호출 후
psql "host=localhost dbname=userdb user=user_app password=dev-userpass" \
     -c "SELECT event_time, event_type, actor, result FROM audit_log ORDER BY event_time DESC LIMIT 20;"
```

### 적재가 안 될 때 점검 순서
1. 기동 로그에 `framework-audit` 빈이 올라왔는가 (모듈 의존 확인).
2. `audit_log` 테이블이 생성됐는가 (`\dt` / H2 콘솔). Flyway `flyway_schema_history` 에 V4(또는 V2) 가 SUCCESS 인지.
3. 앱 로그에 `감사 로그 적재 실패(무시하고 진행)` WARN 이 있는가 → 있으면 컬럼 불일치/테이블 부재.
4. `store.type` 이 실제 `jdbc` 인가 (`logging` 이면 콘솔에만 찍히고 DB 미적재).

> 참고: refresh token(`token-store.type=jdbc`)·common code 등 다른 영속 데이터는 `refresh_tokens`/공통코드 테이블에서 같은 방식으로 확인.

---

## 6. 자주 쓰는 실행 매트릭스

| 목적 | 명령 |
|---|---|
| 메모리 DB(가장 빠름) | `bootRun` |
| 로컬 PostgreSQL | `--args='--spring.profiles.active=local,local-postgres'` |
| PG + Redis | `--args='--spring.profiles.active=local,local-postgres,local-redis'` |
| 로그인 우회 개발 | `--args='--spring.profiles.active=local,local-noauth'` |
| 개발 서버처럼 | `SPRING_PROFILES_ACTIVE=dev DB_URL=... REDIS_HOST=... bootRun` |

환경변수로도 세부 토글 가능: `AUDIT_STORE_TYPE`, `TOKEN_STORE_TYPE`, `FILE_STORAGE_TYPE`, `TRACE_SAMPLING`, `DB_URL/DB_USER/DB_PASSWORD`, `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`.

---

## 부록 A. Docker 로 인프라만 한 번에 (로컬 설치 대안)

서비스는 IDE/Gradle 로 띄우고 PostgreSQL+Redis 만 컨테이너로 쓰고 싶을 때. `infra-compose.yml`:
```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: userdb
      POSTGRES_USER: user_app
      POSTGRES_PASSWORD: dev-userpass
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
  redis:
    image: redis:8
    ports: ["6379:6379"]
volumes:
  pgdata:
```
```bash
docker compose -f infra-compose.yml up -d
# 이후 local,local-postgres,local-redis 로 실행하면 위 컨테이너에 붙는다.
```

## 부록 B. 새 서비스/모듈에서 이 규약 재사용
- `application.yml` 에는 인프라 비종속 공통만, DB/Redis 는 프로파일에서.
- 감사 DB 적재가 필요하면: ① `framework-audit` 의존 ② `audit_log` 마이그레이션 복사 ③ `store.type=jdbc`.
- Redis 가 필요하면: `framework-redis`/`framework-cache-redis` + `spring-boot-starter-data-redis` 의존.
