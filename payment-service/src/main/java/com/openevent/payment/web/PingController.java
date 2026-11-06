package com.openevent.payment.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;

/**
 * Kiểm tra service đã nối với Gateway và nhận được danh tính người gọi.
 * TODO: xóa khi đã có API thật.
 */
@RestController
public class PingController {

    @GetMapping("/api/payments/ping")
    public Map<String, Object> ping(@AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "payment-service");
        response.put("userId", user == null ? null : user.id());
        response.put("roles", user == null ? null : user.roles());
        return response;
    }
}
