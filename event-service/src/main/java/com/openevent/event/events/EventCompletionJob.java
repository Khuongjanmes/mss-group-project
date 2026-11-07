package com.openevent.event.events;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** BR-05: chuyển sự kiện PUBLISHED đã qua end_time sang COMPLETED. Một câu UPDATE nên chạy nhiều instance vẫn an toàn. */
@Component
@RequiredArgsConstructor
class EventCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(EventCompletionJob.class);

    private final EventRepository events;

    @Scheduled(fixedDelayString = "${openevent.events.completion-interval:PT1M}")
    @Transactional
    public void completeEndedEvents() {
        int completed = events.completeEnded(Instant.now());
        if (completed > 0) {
            log.info("Đã chuyển {} sự kiện sang COMPLETED", completed);
        }
    }
}
