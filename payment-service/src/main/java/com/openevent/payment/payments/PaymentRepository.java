package com.openevent.payment.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Khóa dòng để hai callback cùng lúc của một giao dịch chỉ một cái được xử lý (BR-11). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByTransactionRef(String transactionRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Payment> findAllByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    Page<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);
}
