package com.openevent.ticket.tickets;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOwnerIdOrderByIssuedAtDesc(UUID ownerId);

    Optional<Ticket> findByCode(String code);

    /** Hai người quét cùng một vé cùng lúc thì chỉ một người check-in được (BR-09). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Ticket> findWithLockByCode(String code);

    @Query("""
            select t from Ticket t where t.ticketTypeId in
            (select tt.id from TicketType tt where tt.eventId = :eventId) order by t.issuedAt desc, t.id""")
    Page<Ticket> findAllOfEvent(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("""
            select t.ticketTypeId, count(t) from Ticket t where t.status = com.openevent.ticket.tickets.Ticket.Status.CHECKED_IN and t.ticketTypeId in
            (select tt.id from TicketType tt where tt.eventId = :eventId) group by t.ticketTypeId""")
    List<Object[]> checkedInRows(@Param("eventId") UUID eventId);

    default Map<UUID, Long> countCheckedInByType(UUID eventId) {
        return checkedInRows(eventId).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

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
