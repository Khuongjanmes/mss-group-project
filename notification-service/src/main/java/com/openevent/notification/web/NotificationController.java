package com.openevent.notification.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.notification.notifications.NotificationInbox;
import com.openevent.notification.notifications.NotificationInbox.InboxPage;
import com.openevent.notification.notifications.NotificationInbox.UnreadCount;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Hộp thông báo của người đang đăng nhập. Chưa đăng nhập thì Gateway và common-lib trả 401. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInbox inbox;

    /** Thông báo của tôi, mới nhất trước, kèm số chưa đọc. */
    @GetMapping("/my")
    public InboxPage mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inbox.mine(caller.id(), page, size);
    }

    /** Số chưa đọc cho chuông trên thanh điều hướng; SPA gọi định kỳ nên chỉ đếm, không tải danh sách. */
    @GetMapping("/my/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser caller) {
        return inbox.unread(caller.id());
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable @Size(max = 64) String id) {
        inbox.markRead(caller.id(), id);
    }
}
