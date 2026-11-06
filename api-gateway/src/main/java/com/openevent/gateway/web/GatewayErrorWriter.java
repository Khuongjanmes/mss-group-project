package com.openevent.gateway.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Ghi lỗi cùng định dạng với ApiError của các service. */
@Component
public class GatewayErrorWriter {

    private record GatewayError(Instant timestamp, int status, String code, String message, String path) {
    }

    private final JsonMapper json = JsonMapper.builder().build();

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        byte[] body = json.writeValueAsBytes(new GatewayError(Instant.now(), status.value(), code, message,
                exchange.getRequest().getPath().value()));
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
