# 로컬 통합 실행 (docker compose)

`si-msa-framework` 의 4개 서비스(gateway / auth-server / user-service / admin-service)를
PostgreSQL · Redis 와 함께 **로컬에서 한 번에** 띄운다. 서비스 이미지는 **소스부터 컨테이너 안에서
Gradle 로 직접 빌드**한다(A안). 환경값은 `deploy/k8s/overlays/local` 의 ConfigMap/Secret 과 동일하게
맞춰, kind 클러스터에 올렸을 때와 같은 설정 경로(`prod` 프로파일)로 동작한다.

| 구성 | 이미지/빌드 | 포트(host:container) | DB |
|---|---|---|---|
| gateway | source build | `8000:8000` | — |
| auth-server | source build | `9000:9000` | authdb |
| user-service | source build | `8080:8080` | sidb |
| admin-service | source build | `8081:8081` | **admindb** |
| postgres | `postgres:16-alpine` | `5432:5432` | authdb / sidb / admindb |
| redis | `redis:7-alpine` | `6379:6379` | — |

> 전제: 빌드 단계에서 Maven Central 접근(인터넷)이 필요하다. 사내 프록시 환경이면
> Docker Desktop > Settings > Resources > Proxies 를 먼저 설정한다.

---

## 켜는 법

리포지토리 루트에서:

```bash
# 최초/소스 변경 후: 빌드 + 기동
docker compose -f deploy/compose/docker-compose.yml up -d --build

# 상태 확인(전부 healthy 가 목표)
docker compose -f deploy/compose/docker-compose.yml ps

# 로그 따라보기
docker compose -f deploy/compose/docker-compose.yml logs -f auth-server
```

첫 기동은 4개 서비스를 각각 Gradle 빌드하므로 시간이 걸린다(의존성은 BuildKit 캐시로 공유되어
2회차부터 빨라진다). 모든 앱은 `/actuator/health` 가 `UP` 이 되면 `healthy` 로 표시된다.

## 쓰는 법 (동작 검증)

```bash
# 각 서비스 health
curl -s localhost:9000/actuator/health   # auth-server
curl -s localhost:8000/actuator/health   # gateway
curl -s localhost:8080/actuator/health   # user-service
curl -s localhost:8081/actuator/health   # admin-service

# auth-server OIDC discovery
curl -s localhost:9000/.well-known/openid-configuration | head

# DB 스키마(Flyway 적용 결과) 확인
docker compose -f deploy/compose/docker-compose.yml exec postgres \
  psql -U siuser -d sidb -c '\dt'
docker compose -f deploy/compose/docker-compose.yml exec postgres \
  psql -U authuser -d authdb -c '\dt'
```

## 끄는 법

```bash
# 컨테이너만 정지/삭제(DB 데이터는 pgdata 볼륨에 유지)
docker compose -f deploy/compose/docker-compose.yml down

# DB 까지 초기화(Flyway 처음부터 다시 돌리고 싶을 때)
docker compose -f deploy/compose/docker-compose.yml down -v
```

---

## ⚠️ 주의 — admin-service 는 별도 DB(admindb)

k8s `overlays/local` 은 user-service 와 admin-service 를 **둘 다 `sidb`** 로 지정한다.
그런데 두 서비스의 Flyway 마이그레이션이 충돌한다:

- 두 서비스 모두 `flyway.locations=classpath:db/migration`, 기본 history 테이블 `flyway_schema_history` 사용
- 버전·설명이 겹친다: user `V1__init`/`V2__...` vs admin `V1__init`/`V2__audit_log`
- 생성 테이블명도 겹친다: 양쪽 `V1__init` 이 모두 `users/roles/user_roles/resources/menus/...` 를 만들고,
  user `V1` 의 `CREATE TABLE users` 는 `IF NOT EXISTS` 도 아니다.

→ 같은 DB 에 두 서비스가 함께 마이그레이션하면 **체크섬 불일치 + "relation already exists"** 로
두 번째 서비스 기동이 실패한다. 이 compose 는 그래서 **admin-service 만 `admindb` 로 분리**했다
(유일한 의도적 차이). **kind/k8s 로 올릴 때도 같은 문제가 발생**하므로, 운영 전제라면
`deploy/k8s/base/admin-service` 또는 local 오버레이에서 admin 의 DB(또는 `spring.flyway.table`)를
분리하는 것을 권장한다.

## 참고

- **gateway JWT_SECRET 은 다른 서비스와 값이 다르다**(`secrets-local.yaml` 그대로 반영).
  서비스 기동에는 영향 없지만, end-to-end 토큰 검증을 테스트할 때는 게이트웨이가 어떤 토큰
  (프레임워크 HS256 vs AS RS256/JWKS)을 검증하는지에 따라 시크릿 일치 여부를 확인할 것.
- `user-service` 의 `FILE_STORAGE_TYPE` 은 base 의 `s3` 대신 `local` 로 덮었다(로컬엔 S3 없음).
- compose 빌드 컨텍스트 전송을 빠르게 하려면 리포 루트 `.dockerignore` 에 `.git`, `**/.gradle` 추가를
  고려한다. 단 `**/build` 는 제외하지 말 것 — 운영용 `deploy/docker/Dockerfile`(미리 빌드한
  `build/libs` JAR 사용)의 빌드 컨텍스트가 깨진다.
