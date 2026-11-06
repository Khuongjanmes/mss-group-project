package com.openevent.order.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "identity-service")
public interface IdentityClient {

    record UserContact(UUID id, String email, String fullName, String phone, String status) {
    }

    /** KB-1: kiểm tra tài khoản không bị khóa (BR-12). */
    @GetMapping("/internal/users/{id}")
    UserContact getUser(@PathVariable("id") UUID id);
}
