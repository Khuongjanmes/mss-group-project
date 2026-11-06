package com.openevent.gateway.web;

import java.net.ConnectException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/** Trả lỗi JSON thống nhất và không để lộ tên service, địa chỉ nội bộ. Chạy trước handler mặc định (-1). */
@Component
@Order(-2)
class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final GatewayErrorWriter writer;

    GatewayExceptionHandler(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        if (status.is5xxServerError()) {
            log.warn("Lỗi khi xử lý {} {}: {}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(), ex.toString());
        }
        return writer.write(exchange, status, status.name(), message(status));
    }

    private static HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatus) {
            HttpStatus status = HttpStatus.resolve(responseStatus.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex instanceof ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String message(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Không tìm thấy tài nguyên";
            case SERVICE_UNAVAILABLE, BAD_GATEWAY -> "Hệ thống bận, vui lòng thử lại";
            case GATEWAY_TIMEOUT -> "Hệ thống phản hồi quá lâu, vui lòng thử lại";
            case INTERNAL_SERVER_ERROR -> "Lỗi hệ thống";
            default -> status.getReasonPhrase();
        };
    }
}
