package com.openevent.ticket.client;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.openevent.common.web.ApiException;
import com.openevent.ticket.client.EventClient.EventAccess;
import com.openevent.ticket.client.EventClient.EventSummary;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hỏi Event quyền của người gọi (KB-3). Gateway không kiểm tra vai trò cho loại vé và check-in, nên mọi API đó phải
 * qua đây; Event lỗi thì từ chối, trừ check-in được dùng bản cache (chỉ HOST).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDirectory {

    private final EventClient events;

    /** Khoảng diễn ra và HOST của sự kiện, lấy từ Event hoặc từ bản cache. */
    public record EventWindow(UUID organizerId, Instant startTime, Instant endTime, boolean fromEvent) {
    }

    /** Loại vé, thống kê, danh sách người tham dự: cần {@code canManage}. Trả thông tin sự kiện để cập nhật cache. */
    public EventSummary requireManage(UUID eventId, UUID userId) {
        try {
            if (!events.getAccess(eventId, userId).canManage()) {
                throw forbidden("Bạn không quản lý sự kiện này");
            }
            return events.getEvent(eventId);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw translate(eventId, ex);
        }
    }

    /**
     * Check-in: cần {@code canCheckIn}. Event không phản hồi thì dùng bản cache trong {@code ticket_types}: cache
     * chỉ có HOST, nên lúc đó chỉ HOST check-in được.
     */
    public EventWindow requireCheckIn(UUID eventId, UUID userId, EventWindow cache) {
        try {
            EventAccess access = events.getAccess(eventId, userId);
            if (!access.canCheckIn()) {
                throw forbidden("Bạn không phải người quản lý hay STAFF của sự kiện này");
            }
            EventSummary event = events.getEvent(eventId);
            return new EventWindow(event.organizerId(), event.startTime(), event.endTime(), true);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            ApiException translated = translate(eventId, ex);
            if (translated.getStatus() == HttpStatus.SERVICE_UNAVAILABLE && cache.organizerId().equals(userId)) {
                log.warn("Event không phản hồi, check-in bằng bản cache cho HOST của sự kiện {}", eventId);
                return cache;
            }
            throw translated;
        }
    }

    private ApiException translate(UUID eventId, RuntimeException ex) {
        if (causedBy(ex, FeignException.NotFound.class)) {
            return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện");
        }
        log.warn("Event không phản hồi khi kiểm tra quyền sự kiện {}: {}", eventId, ex.toString());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EVENT_UNAVAILABLE",
                "Chưa kiểm tra được quyền với sự kiện, vui lòng thử lại");
    }

    /** Feign bọc lỗi trong ngoại lệ của circuit breaker, nên tìm theo chuỗi nguyên nhân. */
    private static boolean causedBy(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_EVENT_MANAGER", message);
    }
}
