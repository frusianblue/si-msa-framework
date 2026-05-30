INSERT INTO resources (id, url_pattern, http_method, descr, sort_order) VALUES
 (1, '/api/v1/admin/**', 'ALL', '관리자 API', 1);
INSERT INTO role_resources (role_id, resource_id) VALUES (1, 1);

INSERT INTO menus (id, parent_id, name, url, icon, sort_order) VALUES
 (10, NULL, '시스템관리', '/admin', 'cog', 1),
 (11, 10, '권한관리', '/admin/resources', 'lock', 1),
 (12, 10, '메뉴관리', '/admin/menus', 'list', 2);
INSERT INTO role_menus (role_id, menu_id) VALUES (1,10), (1,11), (1,12);
