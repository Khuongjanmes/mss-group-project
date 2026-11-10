package com.openevent.ticket.tickets;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.ticket.messaging.ProcessedMessages;
import com.openevent.ticket.reservations.Reservation;
import com.openevent.ticket.reservations.ReservationRepository;
import com.openevent.ticket.reservations.ReservationService;
import com.openevent.ticket.tickets.dto.TicketResponse;
import com.openevent.ticket.tickettypes.TicketType;
import com.openevent.ticket.tickettypes.TicketTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Phát hành vé (KB-4), hủy vé khi hủy sự kiện (KB-5). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository tickets;
    private final TicketTypeRepository types;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final ProcessedMessages processed;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public List<TicketResponse> mine(UUID ownerId) {
        List<Ticket> owned = tickets.findAllByOwnerIdOrderByIssuedAtDesc(ownerId);
        Map<UUID, TicketType> byId = typesOf(owned);
        return owned.stream().map(ticket -> TicketResponse.from(ticket, byId.get(ticket.getTicketTypeId()))).toList();
    }

    /**
     * KB-4 bước 3: xác nhận phần giữ và phát hành vé (BR-08). Phần giữ đã bị nhả (thanh toán sát giờ hết hạn) thì
     * bán thẳng nếu còn chỗ; hết chỗ thì không phát hành, vẫn không vượt tổng (BR-01).
     */
    @Transactional
    public void issue(OrderPaid event) {
        if (!processed.markNew(event.messageId())) {
            return;
        }
        List<Reservation> found = reservations.findAllWithLockByOrderId(event.orderId());
        if (found.isEmpty()) {
            log.error("OrderPaid cho đơn {} nhưng Ticket không có phần giữ vé nào", event.orderId());
            return;
        }
        Instant now = Instant.now();
        List<String> codes = new ArrayList<>();
        for (Reservation reservation : found) {
            boolean taken = switch (reservation.getStatus()) {
                case CONFIRMED -> false;
                case HELD -> types.confirm(reservation.getTicketTypeId(), reservation.getQuantity()) == 1;
                case RELEASED -> types.sell(reservation.getTicketTypeId(), reservation.getQuantity()) == 1;
            };
            if (!taken) {
                if (reservation.getStatus() != Reservation.Status.CONFIRMED) {
                    log.error("Không còn chỗ để phát hành {} vé loại {} cho đơn {} đã thanh toán; cần hoàn tiền",
                            reservation.getQuantity(), reservation.getTicketTypeId(), event.orderId());
                }
                continue;
            }
            reservation.confirm();
            reservations.save(reservation);
            for (int i = 0; i < reservation.getQuantity(); i++) {
                codes.add(tickets.save(Ticket.issue(reservation.getTicketTypeId(), event.orderId(), event.userId(), now))
                        .getCode());
            }
        }
        if (!codes.isEmpty()) {
            publisher.publishEvent(new TicketIssued(UUID.randomUUID(), now, event.orderId(), event.userId(),
                    event.eventId(), codes));
        }
    }

    /** KB-5 bước 2: hủy vé chưa dùng, nhả vé đang giữ, báo Notification những người cần nhận email. */
    @Transactional
    public void cancelEvent(EventCancelled event) {
        if (!processed.markNew(event.messageId())) {
            return;
        }
        List<UUID> owners = tickets.ownersWithIssuedTickets(event.eventId());
        tickets.cancelIssuedOfEvent(event.eventId());
        reservationService.releaseEvent(event.eventId());
        if (!owners.isEmpty()) {
            publisher.publishEvent(new TicketsCancelled(UUID.randomUUID(), Instant.now(), event.eventId(), owners));
        }
    }

    private Map<UUID, TicketType> typesOf(List<Ticket> list) {
        return types.findAllById(list.stream().map(Ticket::getTicketTypeId).distinct().toList()).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
    }

}
