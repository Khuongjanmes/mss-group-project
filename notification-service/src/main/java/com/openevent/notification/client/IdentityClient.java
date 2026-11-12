package com.openevent.notification.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "identity-service")
public interface IdentityClient {

    /** Identity giới hạn mỗi lần hỏi theo lô tối đa 500 id. */
    int MAX_BATCH = 500;

    record UserContact(UUID id, String email, String fullName, String phone, String status) {
    }

    record UserBatch(List<UUID> ids) {
    }

    /** KB-4: lấy email để gửi vé. */
    @GetMapping("/internal/users/{id}")
    UserContact getUser(@PathVariable("id") UUID id);

    /** KB-5: email của những người có vé khi sự kiện bị hủy; id không còn tồn tại thì không có trong kết quả. */
    @PostMapping("/internal/users/batch")
    List<UserContact> getUsers(@RequestBody UserBatch request);
}
