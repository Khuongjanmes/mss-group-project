package com.openevent.payment.payments.dto;

import jakarta.validation.constraints.NotNull;

/** Kết quả người dùng chọn trên trang cổng giả lập. */
public record MockResultRequest(@NotNull Boolean success) {
}
