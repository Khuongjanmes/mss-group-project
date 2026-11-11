package com.openevent.order.orders;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Dùng chỉ mục {@code orders_user_idx (user_id, created_at DESC)}. */
    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
