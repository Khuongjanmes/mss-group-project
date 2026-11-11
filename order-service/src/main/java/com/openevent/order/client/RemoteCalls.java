package com.openevent.order.client;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * KB-1 bước 7: mọi lời gọi Feign của Order đi qua đây. Lỗi mạng, timeout, 5xx được thử lại (Resilience4J retry);
 * lỗi 4xx là kết quả nghiệp vụ (ví dụ 409 hết vé) nên không thử lại; circuit breaker đang mở thì cũng không thử lại.
 * Chỉ dùng cho lời gọi idempotent: đọc dữ liệu, hoặc giữ vé theo cùng {@code orderId}.
 */
@Slf4j
@Component
public class RemoteCalls {

    /** Service kia từ chối yêu cầu (4xx): mang theo mã lỗi và thông điệp của nó. */
    public static final class Rejected extends RuntimeException {

        private final int status;
        private final String code;

        Rejected(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    /** Service kia không phản hồi hoặc lỗi 5xx sau khi đã thử lại, hoặc circuit breaker đang mở. */
    public static final class Unavailable extends RuntimeException {

        Unavailable(String service, Throwable cause) {
            super(service + " không phản hồi", cause);
        }
    }

    private final JsonMapper json;
    private final RetryConfig config;
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();

    public RemoteCalls(JsonMapper json, @Value("${openevent.order.remote.max-attempts:3}") int maxAttempts,
            @Value("${openevent.order.remote.wait:200ms}") Duration wait) {
        this.json = json;
        this.config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(wait)
                .retryOnException(RemoteCalls::worthRetrying)
                .build();
    }

    /** Gọi {@code call}, thử lại khi lỗi tạm thời; ném {@link Rejected} hoặc {@link Unavailable}. */
    public <T> T call(String service, Supplier<T> call) {
        Retry retry = retries.computeIfAbsent(service, name -> Retry.of(name, config));
        try {
            return Retry.decorateSupplier(retry, call).get();
        } catch (RuntimeException ex) {
            FeignException feign = find(ex, FeignException.class);
            if (feign != null && isClientError(feign)) {
                throw rejected(feign);
            }
            log.warn("{} không phản hồi sau {} lần gọi: {}", service, config.getMaxAttempts(), ex.toString());
            throw new Unavailable(service, ex);
        }
    }

    private static boolean worthRetrying(Throwable ex) {
        if (find(ex, CallNotPermittedException.class) != null) {
            return false;
        }
        FeignException feign = find(ex, FeignException.class);
        return feign == null || !isClientError(feign);
    }

    private static boolean isClientError(FeignException ex) {
        return ex.status() >= 400 && ex.status() < 500;
    }

    /** Đọc {@code code}, {@code message} theo định dạng {@code ApiError} chung; thiếu thì dùng mã HTTP. */
    private Rejected rejected(FeignException ex) {
        String code = "REMOTE_" + ex.status();
        String message = "Yêu cầu không hợp lệ";
        try {
            JsonNode body = json.readTree(ex.contentUTF8());
            if (body.hasNonNull("code")) {
                code = body.get("code").asString();
            }
            if (body.hasNonNull("message")) {
                message = body.get("message").asString();
            }
        } catch (RuntimeException ignored) {
            // Thân lỗi không phải JSON: giữ mã HTTP.
        }
        return new Rejected(ex.status(), code, message);
    }

    /** Spring Cloud CircuitBreaker bọc lỗi Feign, nên tìm theo chuỗi nguyên nhân. */
    private static <E extends Throwable> E find(Throwable ex, Class<E> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }
}
