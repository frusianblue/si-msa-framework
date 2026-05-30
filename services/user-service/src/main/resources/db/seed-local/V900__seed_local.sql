-- 테스트 사용자 ({noop} = 개발용 평문, 운영은 {bcrypt})
INSERT INTO users (id, login_id, password, name, email, phone, role, created_at, created_by)
VALUES (1, 'admin', '{noop}admin123', '관리자', 'admin@test.com', '010-0000-0000', 'ADMIN', CURRENT_TIMESTAMP, 'system');
INSERT INTO users (id, login_id, password, name, email, phone, role, created_at, created_by)
VALUES (2, 'hong', '{noop}hong123', '홍길동', 'hong@test.com', '010-1234-5678', 'USER', CURRENT_TIMESTAMP, 'system');
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1), (1, 2), (2, 2);

-- URL-권한 (조회 USER, 생성 ADMIN)
INSERT INTO resources (id, url_pattern, http_method, descr, sort_order) VALUES
 (1, '/api/v1/users/**', 'GET',  '사용자 조회', 1),
 (2, '/api/v1/users/**', 'POST', '사용자 생성', 2),
 (3, '/api/v1/menus/**', 'ALL',  '메뉴 조회',  3),
 (4, '/api/v1/common-codes/**', 'GET', '공통코드 조회', 4);
INSERT INTO role_resources (role_id, resource_id) VALUES
 (2,1), (1,1), (1,2), (1,3), (2,3), (1,4), (2,4);

-- 메뉴
INSERT INTO menus (id, parent_id, name, url, icon, sort_order) VALUES
 (1, NULL, '사용자관리', '/users', 'user', 1),
 (2, 1, '사용자목록', '/users/list', 'list', 1),
 (3, NULL, '시스템관리', '/system', 'cog', 2);
INSERT INTO role_menus (role_id, menu_id) VALUES (2,1), (2,2), (1,1), (1,2), (1,3);
