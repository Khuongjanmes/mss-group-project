package com.openevent.event.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.openevent.common.web.ApiException;
import com.openevent.event.client.IdentityClient.UserContact;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Tra user ở Identity cho việc mời thành viên, thêm STAFF và hiện họ tên. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final IdentityClient identity;

    public UserContact findByEmail(String email) {
        try {
            return identity.getByEmail(email.trim());
        } catch (FeignException.NotFound ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                    "Không có tài khoản OpenEvent nào dùng email này");
        } catch (FeignException ex) {
            log.warn("Identity không phản hồi khi tìm user theo email: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                    "Hệ thống bận, vui lòng thử lại");
        }
    }

    /** Identity lỗi thì trả rỗng: danh sách vẫn hiện, chỉ thiếu họ tên và email. */
    public Map<UUID, UserContact> contacts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return identity.getBatch(new IdentityClient.UserBatch(ids)).stream()
                    .collect(Collectors.toMap(UserContact::id, Function.identity()));
        } catch (FeignException ex) {
            log.warn("Không lấy được thông tin user từ Identity: {}", ex.getMessage());
            return Map.of();
        }
    }
}
