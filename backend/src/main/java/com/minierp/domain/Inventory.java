package com.minierp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stock position for one (item, location) pair.
 * Available quantity is always derived: physical - reserved. Never stored.
 * All mutations go through guarded atomic updates (see docs/CONCURRENCY.md);
 * the optimistic-lock version column is a secondary safety net.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "physical_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal physicalQty = BigDecimal.ZERO;

    @Column(name = "reserved_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedQty = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public Inventory(Item item, Location location) {
        this.item = item;
        this.location = location;
    }

    public BigDecimal getAvailableQty() {
        return physicalQty.subtract(reservedQty);
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public Location getLocation() {
        return location;
    }

    public BigDecimal getPhysicalQty() {
        return physicalQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
