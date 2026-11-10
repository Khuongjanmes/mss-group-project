package com.openevent.ticket.tickets;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOwnerIdOrderByIssuedAtDesc(UUID ownerId);

}
