package com.openevent.event.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** Nạp sẵn danh mục và địa điểm để danh sách không truy vấn thêm từng dòng. */
    @Override
    @EntityGraph(attributePaths = {"category", "venue"})
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByVenueId(UUID venueId);

    /** BR-05: sự kiện đã công bố quá giờ kết thúc chuyển sang COMPLETED. */
    @Modifying
    @Query("update Event e set e.status = com.openevent.event.events.EventStatus.COMPLETED "
            + "where e.status = com.openevent.event.events.EventStatus.PUBLISHED and e.endTime <= :now")
    int completeEnded(@Param("now") Instant now);
}
