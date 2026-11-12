package com.openevent.event.events;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Tab "Sắp tới / Đang diễn ra / Đã kết thúc" của trang sự kiện, tính theo thời điểm gọi. */
public enum EventTimeline {
    UPCOMING, ONGOING, ENDED;

    /** Đã kết thúc gồm cả sự kiện job chưa kịp chuyển COMPLETED; sự kiện đã hủy không hiện. */
    Specification<Event> at(Instant now) {
        return (root, query, cb) -> switch (this) {
            case UPCOMING -> cb.and(cb.equal(root.get("status"), EventStatus.PUBLISHED),
                    cb.greaterThan(root.get("startTime"), now));
            case ONGOING -> cb.and(cb.equal(root.get("status"), EventStatus.PUBLISHED),
                    cb.lessThanOrEqualTo(root.get("startTime"), now), cb.greaterThan(root.get("endTime"), now));
            case ENDED -> cb.and(root.get("status").in(List.of(EventStatus.PUBLISHED, EventStatus.COMPLETED)),
                    cb.lessThanOrEqualTo(root.get("endTime"), now));
        };
    }

    /** Đã kết thúc: vừa xong trước; còn lại: sắp diễn ra trước. */
    Sort sort() {
        return this == ENDED ? Sort.by(Sort.Order.desc("endTime"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id"));
    }
}
