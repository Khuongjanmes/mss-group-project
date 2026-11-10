package com.openevent.ticket.reservations;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.ticket.reservations.dto.ReservationRequest;
import com.openevent.ticket.reservations.dto.ReservationResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API cho Order gọi qua Feign (KB-1). Gateway chặn /internal/** từ bên ngoài. */
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private final ReservationService reservations;

    /** Hết vé hoặc chưa mở bán trả 409. Gọi lại cùng orderId, kể cả đồng thời, nhận lại phần đã giữ. */
    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) {
        try {
            return reservations.reserve(request);
        } catch (DataIntegrityViolationException ex) {
            return reservations.existing(request.orderId());
        }
    }
}
