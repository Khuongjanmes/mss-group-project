package com.openevent.ticket.tickets;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOwnerIdOrderByIssuedAtDesc(UUID ownerId);

    @Query("""
            select distinct t.ownerId from Ticket t where t.status = com.openevent.ticket.tickets.Ticket.Status.ISSUED and t.ticketTypeId in
            (select tt.id from TicketType tt where tt.eventId = :eventId)""")
    List<UUID> ownersWithIssuedTickets(@Param("eventId") UUID eventId);

    /** KB-5: vé chưa dùng của sự kiện bị hủy; vé đã check-in giữ nguyên để còn lịch sử. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE tickets SET status = 'CANCELLED' WHERE status = 'ISSUED'
            AND ticket_type_id IN (SELECT id FROM ticket_types WHERE event_id = :eventId)""", nativeQuery = true)
    int cancelIssuedOfEvent(@Param("eventId") UUID eventId);
}
