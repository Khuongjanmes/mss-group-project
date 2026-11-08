package com.openevent.event.organization;

/**
 * Vai trò trong tổ chức (BR-14). OWNER và MANAGER quản lý mọi sự kiện của tổ chức; MEMBER chỉ quản lý sự kiện mình
 * làm HOST và chỉ tạo được sự kiện khi OWNER cấp quyền.
 */
public enum OrganizationRole {
    OWNER,
    MANAGER,
    MEMBER;

    public boolean managesAllEvents() {
        return this != MEMBER;
    }
}
