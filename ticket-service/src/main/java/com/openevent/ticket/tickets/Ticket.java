package com.openevent.ticket.tickets;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một vé đã phát hành; {@code code} in trong QR, đoán không được (BR-08). */
@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    public enum Status {
        ISSUED, CHECKED_IN, CANCELLED
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ISSUED;

    private Instant checkedInAt;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    public static Ticket issue(UUID ticketTypeId, UUID orderId, UUID ownerId, Instant now) {
        Ticket ticket = new Ticket();
        ticket.ticketTypeId = ticketTypeId;
        ticket.orderId = orderId;
        ticket.ownerId = ownerId;
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        ticket.code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ticket.issuedAt = now;
        return ticket;
    }

}
