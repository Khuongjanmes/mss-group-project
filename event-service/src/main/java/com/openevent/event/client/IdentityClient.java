package com.openevent.event.client;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Mời thành viên tổ chức bằng email và hiện họ tên thành viên. */
@FeignClient(name = "identity-service")
public interface IdentityClient {

    /** {@code status} là ACTIVE hoặc LOCKED. */
    record UserContact(UUID id, String email, String fullName, String status, boolean admin) {
    }

    record UserBatch(Collection<UUID> ids) {
    }

    @GetMapping("/internal/users/by-email")
    UserContact getByEmail(@RequestParam("email") String email);

    /** Tối đa 500 id mỗi lần (giới hạn của Identity). */
    @PostMapping("/internal/users/batch")
    List<UserContact> getBatch(@RequestBody UserBatch request);
}
