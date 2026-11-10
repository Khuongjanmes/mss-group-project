package com.openevent.ticket.tickets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.common.web.ApiException;
import com.openevent.ticket.client.EventDirectory;
import com.openevent.ticket.client.EventDirectory.EventWindow;
import com.openevent.ticket.messaging.ProcessedMessages;
import com.openevent.ticket.reservations.Reservation;
import com.openevent.ticket.reservations.ReservationRepository;
import com.openevent.ticket.reservations.ReservationService;
import com.openevent.ticket.tickets.dto.TicketResponse;
import com.openevent.ticket.tickettypes.TicketType;
import com.openevent.ticket.tickettypes.TicketTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Phát hành vé (KB-4), hủy vé khi hủy sự kiện (KB-5), check-in (KB-3, BR-09). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    /** Ngày diễn ra tính theo giờ Việt Nam. */
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TicketRepository tickets;
    private final TicketTypeRepository types;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final ProcessedMessages processed;
    private final EventDirectory events;
    private final ApplicationEventPublisher publisher;
    /** Gọi Event ngoài transaction: không giữ kết nối DB trong lúc chờ mạng. */
    private final TransactionTemplate tx;

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

    /**
     * KB-3: lấy sự kiện từ vé trong DB (không tin client), hỏi Event quyền {@code canCheckIn} ngoài transaction,
     * rồi khóa vé và check-in. Event lỗi thì chỉ HOST theo bản cache check-in được.
     */
    public TicketResponse checkIn(UUID callerId, String code) {
        TicketType type = tx.execute(status -> tickets.findByCode(code.trim())
                .flatMap(ticket -> types.findById(ticket.getTicketTypeId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Không tìm thấy vé")));
        EventWindow window = events.requireCheckIn(type.getEventId(), callerId, new EventWindow(
                type.getEventOrganizerId(), type.getEventStartTime(), type.getEventEndTime(), false));
        return tx.execute(status -> {
            if (window.fromEvent()) {
                types.refreshEventCache(type.getEventId(), window.organizerId(), window.startTime(), window.endTime());
            }
            Ticket ticket = tickets.findWithLockByCode(code.trim()).orElseThrow();
            ticket.requireUsable();
            requireEventDay(window);
            ticket.checkIn(Instant.now());
            return TicketResponse.from(tickets.save(ticket), types.findById(ticket.getTicketTypeId()).orElse(null));
        });
    }

    /** BR-09: chỉ trong khoảng ngày diễn ra, từ ngày bắt đầu đến ngày kết thúc. */
    private static void requireEventDay(EventWindow window) {
        LocalDate today = LocalDate.now(VIETNAM);
        if (today.isBefore(window.startTime().atZone(VIETNAM).toLocalDate())
                || today.isAfter(window.endTime().atZone(VIETNAM).toLocalDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "OUTSIDE_EVENT_DAYS",
                    "Chỉ check-in được trong những ngày sự kiện diễn ra");
        }
    }

    private Map<UUID, TicketType> typesOf(List<Ticket> list) {
        return types.findAllById(list.stream().map(Ticket::getTicketTypeId).distinct().toList()).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
    }

}
