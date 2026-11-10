package com.openevent.ticket.reservations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.ticket.reservations.dto.ReservationRequest;
import com.openevent.ticket.reservations.dto.ReservationResponse;
import com.openevent.ticket.tickettypes.TicketType;
import com.openevent.ticket.tickettypes.TicketTypeRepository;

import lombok.extern.slf4j.Slf4j;

/** KB-1 bước 5: giữ vé tất cả hoặc không; BR-02: nhả vé khi đơn hủy hoặc hết hạn. */
@Slf4j
@Service
public class ReservationService {

    private final ReservationRepository reservations;
    private final TicketTypeRepository types;
    private final Duration holdFor;

    public ReservationService(ReservationRepository reservations, TicketTypeRepository types,
            @Value("${openevent.ticket.hold-for:PT10M}") Duration holdFor) {
        this.reservations = reservations;
        this.types = types;
        this.holdFor = holdFor;
    }

    /**
     * Mọi loại vé phải cùng một sự kiện và đang mở bán. Giữ theo thứ tự id để hai đơn cùng lúc không khóa chéo nhau.
     * Một loại không đủ vé thì 409 và rollback cả đơn. Gọi lại cùng orderId trả lại phần đã giữ.
     */
    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        List<Reservation> existing = reservations.findAllByOrderId(request.orderId());
        if (!existing.isEmpty()) {
            return toResponse(request.orderId(), existing);
        }
        if (new HashSet<>(request.items().stream().map(ReservationRequest.Item::ticketTypeId).toList()).size()
                != request.items().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_TICKET_TYPE", "Mỗi loại vé chỉ ghi một lần");
        }
        Instant now = Instant.now();
        UUID eventId = null;
        List<Reservation> held = new ArrayList<>();
        for (ReservationRequest.Item item : request.items().stream()
                .sorted(Comparator.comparing(ReservationRequest.Item::ticketTypeId)).toList()) {
            TicketType type = types.findById(item.ticketTypeId()).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Không tìm thấy loại vé"));
            if (eventId != null && !eventId.equals(type.getEventId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MIXED_EVENTS", "Các loại vé phải cùng một sự kiện");
            }
            eventId = type.getEventId();
            if (!type.onSale(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "SALE_NOT_OPEN",
                        "Loại vé \"" + type.getName() + "\" chưa mở bán hoặc đã hết thời gian bán");
            }
            if (types.reserve(type.getId(), item.quantity()) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT",
                        "Loại vé \"" + type.getName() + "\" không còn đủ " + item.quantity() + " vé");
            }
            // saveAndFlush: gọi lại cùng orderId đồng thời thì vi phạm unique ngay tại đây, không phải lúc commit.
            held.add(reservations.saveAndFlush(Reservation.hold(request.orderId(), type.getId(), item.quantity(),
                    type.getPrice(), now.plus(holdFor))));
        }
        return toResponse(request.orderId(), held);
    }

    @Transactional(readOnly = true)
    public ReservationResponse existing(UUID orderId) {
        List<Reservation> found = reservations.findAllByOrderId(orderId);
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Không có vé đang giữ cho đơn này");
        }
        return toResponse(orderId, found);
    }

    private ReservationResponse toResponse(UUID orderId, List<Reservation> found) {
        UUID eventId = types.findById(found.getFirst().getTicketTypeId()).map(TicketType::getEventId).orElse(null);
        return new ReservationResponse(orderId, eventId, found.getFirst().getExpiresAt(),
                found.stream().map(ReservationResponse.Item::from).toList());
    }
}
