package com.openevent.ticket.tickettypes;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.ticket.tickettypes.dto.TicketTypeRequest;
import com.openevent.ticket.tickettypes.dto.TicketTypeResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** GET công khai; ghi và thống kê chỉ người quản lý sự kiện, do Event xác nhận (KB-3 bước 4). */
@RestController
@RequestMapping("/api/ticket-types")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypes;

    @GetMapping
    public List<TicketTypeResponse> list(@RequestParam UUID eventId) {
        return ticketTypes.list(eventId);
    }

    @GetMapping("/{id}")
    public TicketTypeResponse get(@PathVariable UUID id) {
        return ticketTypes.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody TicketTypeRequest request) {
        return ticketTypes.create(caller.id(), request);
    }

    @PutMapping("/{id}")
    public TicketTypeResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody TicketTypeRequest request) {
        return ticketTypes.update(caller.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        ticketTypes.delete(caller.id(), id);
    }
}
