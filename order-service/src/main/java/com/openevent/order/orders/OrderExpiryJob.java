package com.openevent.order.orders;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BR-02: mỗi phút chuyển đơn PENDING quá hạn sang EXPIRED. Ticket chỉ tự nhả vé sau hạn 2 phút, nên đơn luôn hết
 * hạn trước và Ticket nhả vé nhờ {@code OrderCancelled}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderExpiryJob {

    static final int BATCH = 200;

    private final OrderStore store;

    @Scheduled(fixedDelayString = "${openevent.order.expire-interval:60000}")
    void expireDue() {
        Instant now = Instant.now();
        int total = 0;
        int expired;
        do {
            expired = store.expireDue(now, BATCH);
            total += expired;
        } while (expired == BATCH);
        if (total > 0) {
            log.info("Đã chuyển {} đơn quá hạn sang EXPIRED", total);
        }
    }
}
