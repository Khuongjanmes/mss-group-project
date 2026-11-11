package com.openevent.order.orders;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.openevent.common.web.ApiException;
import com.openevent.order.client.EventClient;
import com.openevent.order.client.EventClient.EventSummary;
import com.openevent.order.client.IdentityClient;
import com.openevent.order.client.IdentityClient.UserContact;
import com.openevent.order.client.RemoteCalls;
import com.openevent.order.client.TicketClient;
import com.openevent.order.client.TicketClient.ReservationItem;
import com.openevent.order.client.TicketClient.ReservationRequest;
import com.openevent.order.client.TicketClient.ReservationResponse;
import com.openevent.order.orders.dto.CreateOrderRequest;
import com.openevent.order.orders.dto.OrderResponse;

/**
 * KB-1: đặt vé. Thứ tự: tài khoản còn hoạt động (BR-12) → sự kiện PUBLISHED (BR-04) → giới hạn 5 vé và tạo đơn
 * PENDING (BR-03) → Ticket giữ vé 10 phút (BR-01, BR-02) → kiểm tra vé thuộc đúng sự kiện → ghi đơn giá, tổng tiền.
 * Không có transaction ở đây: các lời gọi mạng nằm giữa những transaction ngắn của {@link OrderStore}.
 */
@Service
public class OrderService {

    static final String IDENTITY = "identity-service";
    static final String EVENT = "event-service";
    static final String TICKET = "ticket-service";

    private final IdentityClient identity;
    private final EventClient events;
    private final TicketClient tickets;
    private final RemoteCalls remote;
    private final OrderStore store;
    private final Duration holdFor;

    public OrderService(IdentityClient identity, EventClient events, TicketClient tickets, RemoteCalls remote,
            OrderStore store, @Value("${openevent.order.hold-for:PT10M}") Duration holdFor) {
        this.identity = identity;
        this.events = events;
        this.tickets = tickets;
        this.remote = remote;
        this.store = store;
        this.holdFor = holdFor;
    }

    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        requireDistinctTicketTypes(request);
        int ticketCount = request.ticketCount();
        if (ticketCount > Quotas.MAX_PER_EVENT) {
            throw quotaExceeded();
        }
        requireActive(fetch(IDENTITY, () -> identity.getUser(userId)));
        requirePublished(fetch(EVENT, () -> events.getEvent(request.eventId())));

        UUID orderId = store.open(userId, request.eventId(), ticketCount, Instant.now().plus(holdFor));
        ReservationResponse reservation;
        try {
            reservation = remote.call(TICKET, () -> tickets.reserve(new ReservationRequest(orderId,
                    request.items().stream().map(item -> new ReservationItem(item.ticketTypeId(), item.quantity()))
                            .toList())));
        } catch (RemoteCalls.Rejected ex) {
            // Ticket giữ "tất cả hoặc không": từ chối thì chưa giữ vé nào, không cần báo Ticket nhả.
            store.abandon(orderId, ticketCount, ex.code(), false);
            throw new ApiException(HttpStatus.valueOf(ex.status()), ex.code(), ex.getMessage());
        } catch (RemoteCalls.Unavailable ex) {
            // Ticket có thể đã giữ vé trước khi timeout: hủy đơn và báo Ticket nhả (KB-1 bước 7).
            store.abandon(orderId, ticketCount, "TICKET_UNAVAILABLE", true);
            throw unavailable();
        }
        if (!request.eventId().equals(reservation.eventId())) {
            // Ticket chỉ kiểm tra thời gian mở bán, không kiểm tra loại vé thuộc sự kiện nào (BR-04).
            store.abandon(orderId, ticketCount, "TICKET_EVENT_MISMATCH", true);
            throw new ApiException(HttpStatus.BAD_REQUEST, "TICKET_EVENT_MISMATCH",
                    "Loại vé không thuộc sự kiện đã chọn");
        }
        return store.reserved(orderId, reservation);
    }

    /** Lỗi 4xx của Identity, Event: không tìm thấy thì 404; còn lại coi như không hợp lệ. */
    private <T> T fetch(String service, Supplier<T> call) {
        try {
            return remote.call(service, call);
        } catch (RemoteCalls.Rejected ex) {
            if (ex.status() == HttpStatus.NOT_FOUND.value()) {
                throw service.equals(EVENT)
                        ? new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện")
                        : new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_FOUND", "Tài khoản không tồn tại");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage());
        } catch (RemoteCalls.Unavailable ex) {
            throw unavailable();
        }
    }

    private static void requireDistinctTicketTypes(CreateOrderRequest request) {
        long distinct = new HashSet<>(request.items().stream().map(CreateOrderRequest.Item::ticketTypeId).toList())
                .size();
        if (distinct != request.items().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_TICKET_TYPE", "Mỗi loại vé chỉ ghi một lần");
        }
    }

    /** BR-12: JWT còn hạn không phản ánh việc tài khoản vừa bị khóa. */
    private static void requireActive(UserContact user) {
        if (!"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa, không đặt vé được");
        }
    }

    /** BR-04: Order kiểm tra trạng thái sự kiện; thời gian mở bán do Ticket kiểm tra. */
    private static void requirePublished(EventSummary event) {
        if (!"PUBLISHED".equals(event.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_NOT_ON_SALE", "Sự kiện chưa mở hoặc đã đóng đặt vé");
        }
    }

    private static ApiException quotaExceeded() {
        return new ApiException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED",
                "Mỗi tài khoản mua tối đa " + Quotas.MAX_PER_EVENT + " vé cho một sự kiện");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY", "Hệ thống bận, vui lòng thử lại");
    }
}
