package com.openevent.order.orders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Dùng chỉ mục {@code orders_user_idx (user_id, created_at DESC)}. */
    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Khóa đơn trước khi đổi trạng thái, để hủy đơn và job hết hạn không cùng trả quota hai lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") UUID id);

    /** BR-02: đơn PENDING quá hạn. {@code SKIP LOCKED}: nhiều instance chạy job cùng lúc thì mỗi bên lấy đơn khác. */
    @Query(value = """
            SELECT * FROM orders WHERE status = 'PENDING' AND expires_at <= :now
            ORDER BY expires_at LIMIT :limit FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<Order> lockExpired(@Param("now") Instant now, @Param("limit") int limit);

    /** KB-5: các đơn còn hiệu lực (PENDING, PAID) của một sự kiện, khóa để không đổi trạng thái song song. */
    @Query(value = """
            SELECT * FROM orders WHERE event_id = :eventId AND status IN ('PENDING', 'PAID')
            FOR UPDATE""", nativeQuery = true)
    List<Order> lockOpenOfEvent(@Param("eventId") UUID eventId);
}
