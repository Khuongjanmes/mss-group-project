package com.openevent.ticket.reservations;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Mục 3.5 bước 3: mỗi phút nhả vé giữ đã quá hạn (cộng thời gian chờ để Order hết hạn đơn trước). */
@Slf4j
@Component
class ReservationExpiryJob {

    private final ReservationService reservations;
    private final Duration grace;

    ReservationExpiryJob(ReservationService reservations,
            @Value("${openevent.ticket.release-grace:PT2M}") Duration grace) {
        this.reservations = reservations;
        this.grace = grace;
    }

    @Scheduled(fixedDelayString = "${openevent.ticket.release-interval:60000}")
    void releaseExpired() {
        int released = reservations.releaseExpired(Instant.now().minus(grace));
        if (released > 0) {
            log.info("Đã nhả {} lượt giữ vé hết hạn", released);
        }
    }
}
