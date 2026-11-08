-- MEMBER: thành viên thường, chỉ tạo sự kiện khi OWNER cấp quyền (BR-14).
ALTER TABLE organization_members DROP CONSTRAINT organization_members_role_check;
ALTER TABLE organization_members ADD CONSTRAINT organization_members_role_check
    CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER'));
ALTER TABLE organization_members ADD COLUMN can_create_events BOOLEAN NOT NULL DEFAULT FALSE;
