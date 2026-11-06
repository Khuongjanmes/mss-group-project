package com.openevent.notification.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "identity-service")
public interface IdentityClient {

    record UserContact(UUID id, String email, String fullName, String phone, String status) {
    }

    /** KB-4: lấy email để gửi vé. */
    @GetMapping("/internal/users/{id}")
    UserContact getUser(@PathVariable("id") UUID id);
}
