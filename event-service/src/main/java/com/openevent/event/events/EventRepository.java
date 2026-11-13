package com.openevent.event.events;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /** Nạp sẵn danh mục, địa điểm, tổ chức để danh sách không truy vấn thêm từng dòng. */
    @Override
    @EntityGraph(attributePaths = {"category", "venue", "organization"})
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByVenueId(UUID venueId);

    boolean existsByOrganization_IdAndOrganizerIdAndStatusIn(UUID organizationId, UUID organizerId,
            Collection<EventStatus> statuses);

    /**
     * Sự kiện người dùng check-in được (BR-09: HOST, STAFF, OWNER/MANAGER của tổ chức), đã công bố và chưa qua
     * {@code since}, sớm nhất trước.
     */
    @EntityGraph(attributePaths = {"category", "venue", "organization"})
    @Query("select e from Event e where e.status in :statuses and e.endTime > :since and (e.organizerId = :userId "
            + "or :userId member of e.staff or exists (select 1 from Organization o join o.members m "
            + "where o = e.organization and key(m) = :userId "
            + "and m.role <> com.openevent.event.organization.OrganizationRole.MEMBER)) "
            + "order by e.startTime, e.id")
    List<Event> findCheckInable(@Param("userId") UUID userId, @Param("statuses") Collection<EventStatus> statuses,
            @Param("since") Instant since);

    /** BR-05: sự kiện đã công bố quá giờ kết thúc chuyển sang COMPLETED. */
    @Modifying
    @Query("update Event e set e.status = com.openevent.event.events.EventStatus.COMPLETED "
            + "where e.status = com.openevent.event.events.EventStatus.PUBLISHED and e.endTime <= :now")
    int completeEnded(@Param("now") Instant now);
}
