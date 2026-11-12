package com.openevent.ticket.tickettypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Loại vé của một sự kiện. Các cột số lượng chỉ đổi bằng câu UPDATE có điều kiện trong {@link TicketTypeRepository}
 * ({@code updatable = false}), để JPA không ghi đè {@code reserved_quantity} bằng giá trị cũ (mục 3.5).
 */
@Entity
@Table(name = "ticket_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private int totalQuantity;

    @Column(nullable = false, updatable = false)
    private int soldQuantity;

    @Column(nullable = false, updatable = false)
    private int reservedQuantity;

    @Column(nullable = false)
    private Instant saleStart;

    @Column(nullable = false)
    private Instant saleEnd;

    /** Bản cache từ Event cho fallback check-in (KB-3); cập nhật bằng {@link TicketTypeRepository#refreshEventCache}. */
    @Column(nullable = false, updatable = false)
    private UUID eventOrganizerId;

    @Column(nullable = false, updatable = false)
    private Instant eventStartTime;

    @Column(nullable = false, updatable = false)
    private Instant eventEndTime;

    /** Chỉ cho tên, giá, thời gian mở bán. */
    @Version
    private long version;

    public static TicketType create(UUID eventId, String name, BigDecimal price, int totalQuantity, Instant saleStart,
            Instant saleEnd, UUID eventOrganizerId, Instant eventStartTime, Instant eventEndTime) {
        TicketType type = new TicketType();
        type.eventId = eventId;
        type.name = name;
        type.price = price;
        type.totalQuantity = totalQuantity;
        type.saleStart = saleStart;
        type.saleEnd = saleEnd;
        type.eventOrganizerId = eventOrganizerId;
        type.eventStartTime = eventStartTime;
        type.eventEndTime = eventEndTime;
        return type;
    }

    public void describe(String name, BigDecimal price, Instant saleStart, Instant saleEnd) {
        this.name = name;
        this.price = price;
        this.saleStart = saleStart;
        this.saleEnd = saleEnd;
    }

    public int available() {
        return totalQuantity - soldQuantity - reservedQuantity;
    }

    /** BR-04: Ticket kiểm tra thời gian mở bán. */
    public boolean onSale(Instant now) {
        return !now.isBefore(saleStart) && now.isBefore(saleEnd);
    }
}
