package com.openevent.payment.payments;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.payment.payments.dto.MockResultRequest;
import com.openevent.payment.payments.dto.PaymentRequest;
import com.openevent.payment.payments.dto.PaymentResponse;
import com.openevent.payment.payments.dto.PaymentStartResponse;
import com.openevent.payment.gateway.PaymentProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties properties;

    /** KB-2: trả URL để SPA chuyển người dùng sang cổng thanh toán. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentStartResponse start(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody PaymentRequest request, HttpServletRequest http) {
        return paymentService.start(caller.id(), request.orderId(), clientIp(http));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return paymentService.get(caller, id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<PaymentResponse> search(@RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return paymentService.search(status, page, size);
    }

    @PutMapping("/{id}/refunded")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse markRefunded(@PathVariable UUID id) {
        return paymentService.markRefunded(id);
    }

    @PostMapping("/{id}/mock-result")
    public PaymentResponse completeMock(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody MockResultRequest request) {
        return paymentService.completeMock(caller.id(), id, request.success());
    }

    /** Công khai: payOS gọi khi có tiền vào; chữ ký sai thì 400. */
    @PostMapping("/payos-webhook")
    public Map<String, Boolean> payOsWebhook(@RequestBody JsonNode payload) {
        paymentService.handlePayOsWebhook(payload);
        return Map.of("success", true);
    }

    /**
     * Công khai: VNPay chuyển trình duyệt về đây. Xử lý xong thì chuyển tiếp sang trang kết quả của SPA, kèm id
     * giao dịch hoặc mã lỗi.
     */
    @GetMapping("/vnpay-return")
    public ResponseEntity<Void> vnPayReturn(@RequestParam Map<String, String> query, HttpServletRequest http) {
        UriComponentsBuilder result = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path("/payments/result");
        try {
            result.queryParam("paymentId", paymentService.handleVnPayReturn(query, http.getQueryString()));
        } catch (ApiException ex) {
            result.queryParam("error", ex.getCode());
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.build().toUriString())).build();
    }

    /** Gateway thêm X-Forwarded-For; VNPay chỉ dùng để ghi nhận, không dùng để phân quyền. */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? http.getRemoteAddr() : forwarded.split(",")[0].trim();
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
