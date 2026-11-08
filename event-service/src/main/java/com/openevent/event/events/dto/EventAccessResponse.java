package com.openevent.event.events.dto;

/**
 * Quyền của một user trên sự kiện, cho Ticket và Order (KB-3).
 *
 * @param canManage HOST, hoặc OWNER/MANAGER của tổ chức: quản lý loại vé, thống kê, danh sách người tham dự
 * @param canCheckIn {@code canManage} hoặc STAFF
 */
public record EventAccessResponse(boolean canManage, boolean canCheckIn) {
}
