package com.openevent.event.events;

/** Vòng đời theo BR-05. */
public enum EventStatus {
    DRAFT, PENDING_APPROVAL, PUBLISHED, REJECTED, CANCELLED, COMPLETED;

    /** Trạng thái khách và người dùng khác được xem chi tiết. */
    public boolean isPublic() {
        return this == PUBLISHED || this == CANCELLED || this == COMPLETED;
    }
}
