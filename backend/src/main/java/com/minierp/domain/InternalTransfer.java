package com.minierp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stock movement between two locations of the same company.
 * Lifecycle: REQUESTED -> DISPATCHED (source decreases, destination untouched)
 * -> RECEIVED (destination increases). Receipt is single-shot; the status guard
 * in {@code InternalTransferRepository.markReceived} makes double receipt impossible.
 */
@Entity
@Table(name = "internal_transfers")
public class InternalTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_number", nullable = false, unique = true, length = 24)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Location destinationLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by")
    private User dispatchedBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InternalTransfer() {
    }

    public InternalTransfer(String transferNumber, Location sourceLocation, Location destinationLocation,
                            Item item, BigDecimal quantity, User createdBy) {
        this.transferNumber = transferNumber;
        this.sourceLocation = sourceLocation;
        this.destinationLocation = destinationLocation;
        this.item = item;
        this.quantity = quantity;
        this.createdBy = createdBy;
    }

    public boolean isDispatchable() {
        return status == TransferStatus.REQUESTED;
    }

    public boolean isReceivable() {
        return status == TransferStatus.DISPATCHED;
    }

    public Long getId() {
        return id;
    }

    public String getTransferNumber() {
        return transferNumber;
    }

    public Location getSourceLocation() {
        return sourceLocation;
    }

    public Location getDestinationLocation() {
        return destinationLocation;
    }

    public Item getItem() {
        return item;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public User getDispatchedBy() {
        return dispatchedBy;
    }

    public void setDispatchedBy(User dispatchedBy) {
        this.dispatchedBy = dispatchedBy;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(User receivedBy) {
        this.receivedBy = receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
