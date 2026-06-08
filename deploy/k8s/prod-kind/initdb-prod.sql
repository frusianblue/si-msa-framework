-- deploy/k8s/prod-kind/initdb-prod.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- prod 리허설 데이터(K8s 밖) postgres 컨테이너의 1회성 initdb 시드.
--   /docker-entrypoint-initdb.d 규약 = PGDATA 가 빈 최초 부팅에만 실행(PITFALLS §9).
--   실 prod 는 DBA 가 백업·HA·감사 분리 관리 — 이건 "DB=K8s 밖" 토폴로지의 *리허설* 시드일 뿐.
--
-- 서비스별 전용 계정·DB(최소권한 원칙): 각 앱은 자기 DB 만 접근한다.
--   auth-server  → auth_app  / authdb   (jdbc:postgresql://prod-postgres.internal:5432/authdb)
--   user-service → user_app  / userdb   (jdbc:postgresql://prod-postgres.internal:5432/userdb)
--   admin-service→ admin_app / admindb  (jdbc:postgresql://prod-postgres.internal:5432/admindb)
-- ⚠️ user↔admin 는 처음부터 별도 DB(userdb/admindb)·별도 계정 → Flyway 충돌(PITFALLS §9) 원천 차단.
--    비밀번호는 35-seed-secrets.sh 의 서비스별 DB_PASSWORD 와 반드시 일치.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE ROLE auth_app  WITH LOGIN PASSWORD 'auth_app_pw';
CREATE ROLE user_app  WITH LOGIN PASSWORD 'user_app_pw';
CREATE ROLE admin_app WITH LOGIN PASSWORD 'admin_app_pw';

CREATE DATABASE authdb  OWNER auth_app;
CREATE DATABASE userdb  OWNER user_app;
CREATE DATABASE admindb OWNER admin_app;

GRANT ALL PRIVILEGES ON DATABASE authdb  TO auth_app;
GRANT ALL PRIVILEGES ON DATABASE userdb  TO user_app;
GRANT ALL PRIVILEGES ON DATABASE admindb TO admin_app;
