package com.openevent.ticket.reservations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findAllByOrderId(UUID orderId);

    /** Nhiều instance chạy job cùng lúc thì mỗi dòng chỉ một instance lấy được. */
    @Query(value = """
            SELECT * FROM reservations WHERE status = 'HELD' AND expires_at < :cutoff
            ORDER BY expires_at LIMIT 200 FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<Reservation> lockExpired(@Param("cutoff") Instant cutoff);

}
