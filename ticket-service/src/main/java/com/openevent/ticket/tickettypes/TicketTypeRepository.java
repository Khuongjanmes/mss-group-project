package com.openevent.ticket.tickettypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi thay đổi số lượng là một câu UPDATE có điều kiện: số dòng bằng 0 nghĩa là không đủ vé (BR-01). */
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findAllByEventIdOrderByPriceAscNameAsc(UUID eventId);

    /** BR-06: không giảm tổng xuống dưới số đã bán cộng đang giữ. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE ticket_types SET total_quantity = :n, version = version + 1
            WHERE id = :id AND :n >= sold_quantity + reserved_quantity""", nativeQuery = true)
    int resize(@Param("id") UUID id, @Param("n") int totalQuantity);

    /** Chỉ xóa loại vé chưa từng được giữ hay bán. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM ticket_types t WHERE t.id = :id AND t.sold_quantity = 0 AND t.reserved_quantity = 0
            AND NOT EXISTS (SELECT 1 FROM reservations r WHERE r.ticket_type_id = t.id)""", nativeQuery = true)
    int deleteUnused(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE ticket_types SET event_organizer_id = :organizerId, event_start_time = :startTime,
            event_end_time = :endTime WHERE event_id = :eventId""", nativeQuery = true)
    int refreshEventCache(@Param("eventId") UUID eventId, @Param("organizerId") UUID organizerId,
            @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
}
