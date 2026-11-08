-- Mọi tài khoản (trừ Admin) đều tổ chức được sự kiện (BR-14): bỏ ORGANIZER, đổi ATTENDEE thành USER.
-- Thêm ATTENDEE cho ai chưa có trước khi xóa ORGANIZER, để không user nào mất hết vai trò.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE r.name = 'ATTENDEE'
ON CONFLICT DO NOTHING;

DELETE FROM user_roles WHERE role_id = (SELECT id FROM roles WHERE name = 'ORGANIZER');
DELETE FROM roles WHERE name = 'ORGANIZER';
UPDATE roles SET name = 'USER' WHERE name = 'ATTENDEE';
