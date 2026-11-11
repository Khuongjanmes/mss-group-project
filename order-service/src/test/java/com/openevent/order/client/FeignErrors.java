package com.openevent.order.client;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import feign.FeignException;
import feign.Request;
import feign.Response;

/** Lỗi Feign giống thật (đúng lớp con theo mã HTTP) để giả lập service khác trả lỗi. */
public final class FeignErrors {

    private FeignErrors() {
    }

    /** {@code body}: JSON theo định dạng {@code ApiError}, hoặc {@code null}. */
    public static FeignException status(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/internal", Map.of(), null,
                StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).reason("test").request(request).headers(Map.of())
                .body(body == null ? null : body.getBytes(StandardCharsets.UTF_8)).build();
        return FeignException.errorStatus("remote", response);
    }
}
