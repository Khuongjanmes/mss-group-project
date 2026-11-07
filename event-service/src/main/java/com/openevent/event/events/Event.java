package com.openevent.event.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.openevent.common.web.ApiException;
import com.openevent.event.category.Category;
import com.openevent.event.venue.Venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Sự kiện và các bước chuyển trạng thái (BR-05, BR-07). Kiểm tra quyền sở hữu do {@link EventService} làm. */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(nullable = false)
    private String title;

    private String description;

    private String bannerUrl;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    private String rejectReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Event draft(UUID organizerId) {
        Event event = new Event();
        event.organizerId = organizerId;
        return event;
    }

    public boolean isOwnedBy(UUID userId) {
        return organizerId.equals(userId);
    }

    /** Chỉ sửa khi chưa gửi duyệt hoặc bị từ chối; sự kiện đã công bố không đổi nội dung. */
    public void updateDetails(String title, String description, String bannerUrl, Category category, Venue venue,
            Instant startTime, Instant endTime, Instant now) {
        requireEditable();
        if (!endTime.isAfter(startTime)) {
            throw invalidTime("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (!startTime.isAfter(now)) {
            throw invalidTime("Thời gian bắt đầu phải ở tương lai");
        }
        this.title = title;
        this.description = description;
        this.bannerUrl = bannerUrl;
        this.category = category;
        this.venue = venue;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void requireEditable() {
        if (status != EventStatus.DRAFT && status != EventStatus.REJECTED) {
            throw statusInvalid("Chỉ sửa hoặc xóa được sự kiện nháp hoặc bị từ chối");
        }
    }

    public void submit(Instant now) {
        requireEditable();
        requireNotStarted(now);
        status = EventStatus.PENDING_APPROVAL;
        rejectReason = null;
    }

    public void approve(Instant now) {
        requireStatus(EventStatus.PENDING_APPROVAL, "Chỉ duyệt được sự kiện đang chờ duyệt");
        requireNotStarted(now);
        status = EventStatus.PUBLISHED;
    }

    public void reject(String reason) {
        requireStatus(EventStatus.PENDING_APPROVAL, "Chỉ từ chối được sự kiện đang chờ duyệt");
        status = EventStatus.REJECTED;
        rejectReason = reason;
    }

    /** Hủy được cả khi đang diễn ra, miễn chưa kết thúc. */
    public void cancel(Instant now) {
        requireStatus(EventStatus.PUBLISHED, "Chỉ hủy được sự kiện đã công bố");
        if (!now.isBefore(endTime)) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_ALREADY_ENDED", "Sự kiện đã kết thúc, không hủy được");
        }
        status = EventStatus.CANCELLED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private void requireNotStarted(Instant now) {
        if (!now.isBefore(startTime)) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_ALREADY_STARTED", "Sự kiện đã bắt đầu");
        }
    }

    private void requireStatus(EventStatus expected, String message) {
        if (status != expected) {
            throw statusInvalid(message);
        }
    }

    private static ApiException statusInvalid(String message) {
        return new ApiException(HttpStatus.CONFLICT, "EVENT_STATUS_INVALID", message);
    }

    private static ApiException invalidTime(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_TIME", message);
    }
}
