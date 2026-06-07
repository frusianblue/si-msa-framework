-- =====================================================================
-- rbac-empty-schema.sql — 데모 전용 "빈" rbac 스키마 (행 0개)
-- ---------------------------------------------------------------------
-- framework-security 의 SecurityMetadataService 는 dynamic-authorization 토글과
-- 무관하게 생성자에서 findAllResources() 를 1회 호출한다(reload). 테이블이 아예
-- 없으면 MyBatis 가 시끄러운 SQL 에러 메시지를 WARN 으로 찍는다(기능엔 무해 — 빈 캐시로 시작).
-- 데모를 "조용히" 부팅시키려고 비어 있는 3개 테이블만 만들어 둔다(행은 넣지 않음 →
-- findAllResources 가 0행을 깔끔히 반환). dynamic-authorization=false 라 이 캐시는 어차피 미사용.
--
-- H2 전용: spring.datasource.url 의 INIT=RUNSCRIPT FROM 'classpath:db/rbac-empty-schema.sql' 로
-- DB 최초 연결 시점에 실행된다(빈 생성 순서와 무관 → reload 전에 테이블 존재 보장).
-- 운영(PostgreSQL)에서는 실제 rbac 스키마를 앱 마이그레이션으로 제공하므로 이 파일과 무관.
-- =====================================================================
CREATE TABLE IF NOT EXISTS roles (
    id        BIGINT       PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS resources (
    id          BIGINT       PRIMARY KEY,
    url_pattern VARCHAR(255) NOT NULL,
    http_method VARCHAR(10)  NOT NULL,
    sort_order  INT          NOT NULL
);

CREATE TABLE IF NOT EXISTS role_resources (
    resource_id BIGINT NOT NULL,
    role_id     BIGINT NOT NULL
);
