package com.openevent.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import tools.jackson.databind.json.JsonMapper;

/** KB-1 bước 7: thử lại lỗi tạm thời, không thử lại lỗi nghiệp vụ 4xx. */
class RemoteCallsTest {

    private final RemoteCalls remote = new RemoteCalls(JsonMapper.builder().build(), 3, Duration.ZERO);
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void returnsResultWithoutRetryWhenCallSucceeds() {
        assertThat(remote.call("svc", () -> calls.incrementAndGet())).isEqualTo(1);
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesServerErrorsThenReportsUnavailable() {
        assertThatThrownBy(() -> remote.call("svc", () -> {
            calls.incrementAndGet();
            throw FeignErrors.status(500, null);
        })).isInstanceOf(RemoteCalls.Unavailable.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void retriesNetworkErrorsAndSucceedsWhenServiceComesBack() {
        Integer result = remote.call("svc", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("connection refused");
            }
            return 42;
        });
        assertThat(result).isEqualTo(42);
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryClientErrorsAndKeepsRemoteErrorCode() {
        assertThatThrownBy(() -> remote.call("svc", () -> {
            calls.incrementAndGet();
            throw FeignErrors.status(409, "{\"status\":409,\"code\":\"SOLD_OUT\",\"message\":\"Loại vé đã hết\"}");
        })).isInstanceOfSatisfying(RemoteCalls.Rejected.class, ex -> {
            assertThat(ex.status()).isEqualTo(409);
            assertThat(ex.code()).isEqualTo("SOLD_OUT");
            assertThat(ex.getMessage()).isEqualTo("Loại vé đã hết");
        });
        assertThat(calls).hasValue(1);
    }

    @Test
    void findsClientErrorWrappedByCircuitBreaker() {
        assertThatThrownBy(() -> remote.call("svc", () -> {
            calls.incrementAndGet();
            throw new RuntimeException("no fallback", FeignErrors.status(404, "not json"));
        })).isInstanceOfSatisfying(RemoteCalls.Rejected.class, ex -> {
            assertThat(ex.status()).isEqualTo(404);
            assertThat(ex.code()).isEqualTo("REMOTE_404");
        });
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryWhenCircuitBreakerIsOpen() {
        CircuitBreaker open = CircuitBreaker.ofDefaults("ticket-service");
        open.transitionToForcedOpenState();
        assertThatThrownBy(() -> remote.call("svc", () -> {
            calls.incrementAndGet();
            throw new RuntimeException("no fallback", CallNotPermittedException.createCallNotPermittedException(open));
        })).isInstanceOf(RemoteCalls.Unavailable.class);
        assertThat(calls).hasValue(1);
    }
}
