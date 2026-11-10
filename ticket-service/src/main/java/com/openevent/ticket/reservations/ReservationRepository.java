package com.openevent.ticket.reservations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findAllByOrderId(UUID orderId);

    /** Khóa các dòng của đơn để OrderPaid và OrderCancelled đến cùng lúc không xử lý chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findAllWithLockByOrderId(UUID orderId);

    /** Nhiều instance chạy job cùng lúc thì mỗi dòng chỉ một instance lấy được. */
    @Query(value = """
            SELECT * FROM reservations WHERE status = 'HELD' AND expires_at < :cutoff
            ORDER BY expires_at LIMIT 200 FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<Reservation> lockExpired(@Param("cutoff") Instant cutoff);

    @Query(value = """
            SELECT r.* FROM reservations r JOIN ticket_types t ON t.id = r.ticket_type_id
            WHERE t.event_id = :eventId AND r.status = 'HELD' FOR UPDATE OF r""", nativeQuery = true)
    List<Reservation> lockHeldOfEvent(@Param("eventId") UUID eventId);
}
