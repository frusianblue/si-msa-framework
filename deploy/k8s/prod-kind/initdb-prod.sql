-- deploy/k8s/prod-kind/initdb-prod.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- prod 리허설 데이터(K8s 밖) postgres 컨테이너의 1회성 initdb 시드.
--   /docker-entrypoint-initdb.d 규약 = PGDATA 가 빈 최초 부팅에만 실행(PITFALLS §9).
--   실 prod 는 DBA 가 백업·HA·감사 분리 관리 — 이건 "DB=K8s 밖" 토폴로지의 *리허설* 시드일 뿐.
--
-- prod overlay(kustomization.yaml) 가 기대하는 DB:
--   auth-server  → jdbc:postgresql://prod-postgres.internal:5432/authdb
--   user-service → jdbc:postgresql://prod-postgres.internal:5432/sidb
--   admin-service→ jdbc:postgresql://prod-postgres.internal:5432/sidb
-- ⚠️ user↔admin 가 같은 sidb 를 공유하면 Flyway 충돌(PITFALLS §9) — admindb 도 미리 만들어 두어
--    step5(데이터 정합)에서 admin 을 admindb 로 분리할 선택지를 남긴다(여기선 생성만).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE ROLE siuser WITH LOGIN PASSWORD 'siuser_pw';

CREATE DATABASE authdb  OWNER siuser;
CREATE DATABASE sidb    OWNER siuser;
CREATE DATABASE admindb OWNER siuser;

GRANT ALL PRIVILEGES ON DATABASE authdb  TO siuser;
GRANT ALL PRIVILEGES ON DATABASE sidb    TO siuser;
GRANT ALL PRIVILEGES ON DATABASE admindb TO siuser;
