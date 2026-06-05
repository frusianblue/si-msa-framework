-- deploy/compose/initdb/init.sql
-- 최초 1회(빈 데이터 디렉토리)만 postgres entrypoint 가 실행한다.
-- k8s overlays/local/postgres.yaml 의 initdb 와 동일한 계정/DB 규약 + admindb 추가.
--   authuser / dev-authpass → authdb   (auth-server)
--   siuser   / dev-sipass   → sidb     (user-service)
--   siuser   / dev-sipass   → admindb  (admin-service)  ← user-service 와 마이그레이션 충돌 회피용 분리 DB
-- 비밀번호는 secrets-local.yaml 과 일치.

CREATE ROLE authuser WITH LOGIN PASSWORD 'dev-authpass';
CREATE ROLE siuser   WITH LOGIN PASSWORD 'dev-sipass';

CREATE DATABASE authdb  OWNER authuser;
CREATE DATABASE sidb    OWNER siuser;
CREATE DATABASE admindb OWNER siuser;

-- PG15+ 는 public 스키마에 CREATE 권한이 기본 제거되어 있어, 앱 역할에 명시적으로 부여한다.
\connect authdb
GRANT ALL ON SCHEMA public TO authuser;

\connect sidb
GRANT ALL ON SCHEMA public TO siuser;

\connect admindb
GRANT ALL ON SCHEMA public TO siuser;
