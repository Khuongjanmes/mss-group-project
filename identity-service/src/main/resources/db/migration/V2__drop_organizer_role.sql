-- Bỏ vai trò ORGANIZER: mọi tài khoản đều tạo được tổ chức và sự kiện (BR-14).
DELETE FROM user_roles WHERE role_id = (SELECT id FROM roles WHERE name = 'ORGANIZER');
DELETE FROM roles WHERE name = 'ORGANIZER';
