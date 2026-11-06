package com.openevent.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.openevent.order.client.TicketClient;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/** requirement.md KB-1: hết vé (409) không được làm circuit breaker mở; lỗi 5xx thì có. */
@SpringBootTest(properties = "eureka.client.enabled=false")
class FeignCircuitBreakerIT {

    @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final HttpServer TICKET_STUB;

    static {
        DB.start();
        RABBIT.start();
        try {
            TICKET_STUB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        TICKET_STUB.createContext("/", exchange -> {
            CALLS.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(RESPONSE_STATUS.get(), -1);
            exchange.close();
        });
        TICKET_STUB.start();
    }

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.ticket-service[0].uri",
                () -> "http://localhost:" + TICKET_STUB.getAddress().getPort());
    }

    @Autowired
    TicketClient ticketClient;

    @Autowired
    CircuitBreakerRegistry breakers;

    @Autowired
    CircuitBreakerFactory<?, ?> factory;

    @BeforeEach
    void reset() {
        breakers.getAllCircuitBreakers().forEach(breaker -> breaker.reset());
        CALLS.set(0);
    }

    private void callManyTimes(int count) {
        TicketClient.ReservationRequest request = new TicketClient.ReservationRequest(UUID.randomUUID(),
                List.of(new TicketClient.ReservationItem(UUID.randomUUID(), 1)));
        for (int i = 0; i < count; i++) {
            assertThatThrownBy(() -> ticketClient.reserve(request)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void soldOutResponsesDoNotOpenCircuitBreaker() {
        RESPONSE_STATUS.set(409);

        callManyTimes(30);

        assertThat(CALLS).hasValue(30);
    }

    @Test
    void serverErrorsOpenCircuitBreakerAndStopCalls() {
        RESPONSE_STATUS.set(500);

        callManyTimes(30);

        assertThat(CALLS.get()).isLessThan(30);
    }
}
