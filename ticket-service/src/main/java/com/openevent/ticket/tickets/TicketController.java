package com.openevent.ticket.tickets;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.PageResponse;
import com.openevent.ticket.tickets.dto.AttendeeResponse;
import com.openevent.ticket.tickets.dto.CheckInRequest;
import com.openevent.ticket.tickets.dto.TicketResponse;
import com.openevent.ticket.tickettypes.TicketTypeService;
import com.openevent.ticket.tickettypes.dto.TicketTypeStats;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** Mọi API ở đây cần đăng nhập; quyền theo sự kiện do Event xác nhận (KB-3). */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService tickets;
    private final TicketTypeService ticketTypes;

    @GetMapping("/my")
    public List<TicketResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller) {
        return tickets.mine(caller.id());
    }

    /** Người quản lý sự kiện hoặc STAFF. */
    @PostMapping("/check-in")
    public TicketResponse checkIn(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CheckInRequest request) {
        return tickets.checkIn(caller.id(), request.code());
    }

    /** Người quản lý sự kiện: danh sách người tham dự. */
    @GetMapping
    public PageResponse<AttendeeResponse> attendees(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam UUID eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return tickets.attendees(caller.id(), eventId, page, size);
    }

    /** Organizer #7, phần của Ticket: vé đã bán, đang giữ, đã check-in theo loại vé. */
    @GetMapping("/stats")
    public List<TicketTypeStats> stats(@AuthenticationPrincipal AuthenticatedUser caller, @RequestParam UUID eventId) {
        return ticketTypes.stats(caller.id(), eventId);
    }
}
