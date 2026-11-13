package com.openevent.identity.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Khóa dòng: hai lần bấm cùng link không đổi mật khẩu hai lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findWithLockByTokenHash(String tokenHash);

    /** Yêu cầu mới làm link cũ chưa dùng hết hiệu lực. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId and t.usedAt is null")
    int deleteUnusedOf(@Param("userId") UUID userId);
}
