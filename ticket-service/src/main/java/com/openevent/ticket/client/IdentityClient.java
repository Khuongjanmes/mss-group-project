package com.openevent.ticket.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "identity-service")
public interface IdentityClient {

    record UserContact(UUID id, String email, String fullName, String phone, String status) {
    }

    record UserBatchRequest(List<UUID> ids) {
    }

    /** Danh sách người tham dự: tối đa 500 id mỗi lần gọi. */
    @PostMapping("/internal/users/batch")
    List<UserContact> getUsers(@RequestBody UserBatchRequest request);
}
