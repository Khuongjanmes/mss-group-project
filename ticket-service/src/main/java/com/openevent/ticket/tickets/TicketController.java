package com.openevent.ticket.tickets;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.ticket.tickets.dto.CheckInRequest;
import com.openevent.ticket.tickets.dto.TicketResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Mọi API ở đây cần đăng nhập; quyền theo sự kiện do Event xác nhận (KB-3). */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService tickets;

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

}
