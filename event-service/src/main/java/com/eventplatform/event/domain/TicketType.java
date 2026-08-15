package com.eventplatform.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_types")
public class TicketType {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int allocation;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "sales_start", nullable = false)
    private Instant salesStart;

    @Column(name = "sales_end", nullable = false)
    private Instant salesEnd;

    @Column(name = "min_quantity", nullable = false)
    private int minQuantity;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketTypeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    protected TicketType() {
    }

    public TicketType(
            UUID id,
            UUID eventId,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int allocation,
            Instant salesStart,
            Instant salesEnd,
            int minQuantity,
            int maxQuantity,
            TicketTypeStatus status,
            Instant now) {
        this.id = id;
        this.eventId = eventId;
        apply(name, description, price, currency, allocation, salesStart, salesEnd, minQuantity, maxQuantity, status, now);
        this.createdAt = now;
    }

    public void update(
            String name,
            String description,
            BigDecimal price,
            String currency,
            int allocation,
            Instant salesStart,
            Instant salesEnd,
            int minQuantity,
            int maxQuantity,
            TicketTypeStatus status,
            Instant now) {
        apply(name, description, price, currency, allocation, salesStart, salesEnd, minQuantity, maxQuantity, status, now);
    }

    private void apply(
            String name,
            String description,
            BigDecimal price,
            String currency,
            int allocation,
            Instant salesStart,
            Instant salesEnd,
            int minQuantity,
            int maxQuantity,
            TicketTypeStatus status,
            Instant now) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.allocation = allocation;
        this.salesStart = salesStart;
        this.salesEnd = salesEnd;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        this.status = status;
        this.updatedAt = now;
    }

    public void reserve(int quantity, Instant now) {
        reservedQuantity += quantity;
        updatedAt = now;
    }

    public void release(int quantity, Instant now) {
        reservedQuantity = Math.max(0, reservedQuantity - quantity);
        updatedAt = now;
    }

    public void archive(Instant now) {
        status = TicketTypeStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    public int availableQuantity() {
        return allocation - reservedQuantity;
    }

    public boolean isOnSale(Instant now) {
        return status == TicketTypeStatus.ACTIVE
                && !now.isBefore(salesStart)
                && now.isBefore(salesEnd);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public int getAllocation() {
        return allocation;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getSalesStart() {
        return salesStart;
    }

    public Instant getSalesEnd() {
        return salesEnd;
    }

    public int getMinQuantity() {
        return minQuantity;
    }

    public int getMaxQuantity() {
        return maxQuantity;
    }

    public TicketTypeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
