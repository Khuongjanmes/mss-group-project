package com.openevent.ticket.tickettypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.web.ApiException;
import com.openevent.ticket.client.EventClient.EventSummary;
import com.openevent.ticket.client.EventDirectory;
import com.openevent.ticket.tickettypes.dto.TicketTypeRequest;
import com.openevent.ticket.tickettypes.dto.TicketTypeResponse;

import lombok.RequiredArgsConstructor;

/** Organizer #4: quản lý loại vé. Quyền hỏi Event ngoài transaction (KB-3 bước 4), rồi mới ghi. */
@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private static final List<String> CLOSED = List.of("CANCELLED", "COMPLETED");

    private final TicketTypeRepository types;
    private final EventDirectory events;
    private final TransactionTemplate tx;

    @Transactional(readOnly = true)
    public List<TicketTypeResponse> list(UUID eventId) {
        return types.findAllByEventIdOrderByPriceAscNameAsc(eventId).stream().map(TicketTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TicketTypeResponse get(UUID id) {
        return TicketTypeResponse.from(require(id));
    }

    public TicketTypeResponse create(UUID callerId, TicketTypeRequest request) {
        if (request.eventId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EVENT_ID_REQUIRED", "Chọn sự kiện cho loại vé");
        }
        EventSummary event = events.requireManage(request.eventId(), callerId);
        validate(event, request);
        return tx.execute(status -> {
            types.refreshEventCache(event.id(), event.organizerId(), event.startTime(), event.endTime());
            TicketType type = types.saveAndFlush(TicketType.create(event.id(), request.name().trim(), request.price(),
                    request.totalQuantity(), request.saleStart(), request.saleEnd(), event.organizerId(),
                    event.startTime(), event.endTime()));
            return TicketTypeResponse.from(type);
        });
    }

    /** Tên, giá, thời gian mở bán qua entity (có version); số lượng bằng câu UPDATE có điều kiện (BR-06). */
    public TicketTypeResponse update(UUID callerId, UUID id, TicketTypeRequest request) {
        UUID eventId = tx.execute(status -> require(id).getEventId());
        EventSummary event = events.requireManage(eventId, callerId);
        validate(event, request);
        return tx.execute(status -> {
            TicketType type = require(id);
            type.describe(request.name().trim(), request.price(), request.saleStart(), request.saleEnd());
            types.flush();
            if (types.resize(id, request.totalQuantity()) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "QUANTITY_BELOW_SOLD",
                        "Không giảm được số lượng xuống dưới số vé đã bán và đang giữ");
            }
            types.refreshEventCache(eventId, event.organizerId(), event.startTime(), event.endTime());
            return TicketTypeResponse.from(require(id));
        });
    }

    public void delete(UUID callerId, UUID id) {
        UUID eventId = tx.execute(status -> require(id).getEventId());
        events.requireManage(eventId, callerId);
        Integer deleted = tx.execute(status -> types.deleteUnused(id));
        if (deleted == null || deleted == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_TYPE_IN_USE",
                    "Loại vé đã có người giữ hoặc mua, không xóa được; hãy giảm số lượng hoặc đóng mở bán");
        }
    }

    /** BR-07: mở bán kết thúc trước khi sự kiện bắt đầu; sự kiện đã hủy hay kết thúc thì không đổi vé. */
    private static void validate(EventSummary event, TicketTypeRequest request) {
        if (CLOSED.contains(event.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_CLOSED", "Sự kiện đã hủy hoặc đã kết thúc");
        }
        if (!request.saleEnd().isAfter(request.saleStart())) {
            throw invalidSale("Thời gian kết thúc mở bán phải sau thời gian bắt đầu");
        }
        if (request.saleEnd().isAfter(event.startTime())) {
            throw invalidSale("Mở bán phải kết thúc trước khi sự kiện bắt đầu");
        }
        if (!request.saleEnd().isAfter(Instant.now())) {
            throw invalidSale("Thời gian kết thúc mở bán đã qua");
        }
    }

    private TicketType require(UUID id) {
        return types.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Không tìm thấy loại vé"));
    }

    private static ApiException invalidSale(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SALE_TIME", message);
    }
}
