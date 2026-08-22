package com.minierp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only audit row for one stock movement. Written in the same transaction
 * as the inventory mutation it describes.
 */
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private InventoryTxType txType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 20)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "quantity_delta_physical", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityDeltaPhysical = BigDecimal.ZERO;

    @Column(name = "quantity_delta_reserved", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityDeltaReserved = BigDecimal.ZERO;

    @Column(length = 255)
    private String notes;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryTransaction() {
    }

    public InventoryTransaction(Item item, Location location, Batch batch,
                                InventoryTxType txType, ReferenceType referenceType, Long referenceId,
                                BigDecimal quantityDeltaPhysical, BigDecimal quantityDeltaReserved,
                                String notes, String idempotencyKey, User performedBy) {
        this.item = item;
        this.location = location;
        this.batch = batch;
        this.txType = txType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.quantityDeltaPhysical = quantityDeltaPhysical;
        this.quantityDeltaReserved = quantityDeltaReserved;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.performedBy = performedBy;
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

    public Batch getBatch() {
        return batch;
    }

    public InventoryTxType getTxType() {
        return txType;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public BigDecimal getQuantityDeltaPhysical() {
        return quantityDeltaPhysical;
    }

    public BigDecimal getQuantityDeltaReserved() {
        return quantityDeltaReserved;
    }

    public String getNotes() {
        return notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
