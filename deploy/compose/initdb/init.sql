-- deploy/compose/initdb/init.sql
-- 최초 1회(빈 데이터 디렉토리)만 postgres entrypoint 가 실행한다.
-- k8s overlays/local/postgres.yaml 의 initdb 와 동일한 계정/DB 규약.
--   서비스별 전용 계정·DB(최소권한): 각 앱은 자기 DB 만 접근한다.
--   auth_app  / dev-authpass  → authdb   (auth-server)
--   user_app  / dev-userpass  → userdb   (user-service)
--   admin_app / dev-adminpass → admindb  (admin-service)  ← user-service 와 마이그레이션 충돌 회피용 분리 DB
-- 비밀번호는 secrets-local.yaml 과 일치.

CREATE ROLE auth_app  WITH LOGIN PASSWORD 'dev-authpass';
CREATE ROLE user_app  WITH LOGIN PASSWORD 'dev-userpass';
CREATE ROLE admin_app WITH LOGIN PASSWORD 'dev-adminpass';

CREATE DATABASE authdb  OWNER auth_app;
CREATE DATABASE userdb  OWNER user_app;
CREATE DATABASE admindb OWNER admin_app;

-- PG15+ 는 public 스키마에 CREATE 권한이 기본 제거되어 있어, 앱 역할에 명시적으로 부여한다.
\connect authdb
GRANT ALL ON SCHEMA public TO auth_app;

\connect userdb
GRANT ALL ON SCHEMA public TO user_app;

\connect admindb
GRANT ALL ON SCHEMA public TO admin_app;
