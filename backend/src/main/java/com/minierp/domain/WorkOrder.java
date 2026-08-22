package com.minierp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wo_number", nullable = false, unique = true, length = 24)
    private String woNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "required_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal requiredQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_user_id", nullable = false)
    private User assignedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus status = WorkOrderStatus.ASSIGNED;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkOrder() {
    }

    public WorkOrder(String woNumber, Item item, Location location,
                     BigDecimal requiredQuantity, User assignedUser, User createdBy) {
        this.woNumber = woNumber;
        this.item = item;
        this.location = location;
        this.requiredQuantity = requiredQuantity;
        this.assignedUser = assignedUser;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getWoNumber() {
        return woNumber;
    }

    public Item getItem() {
        return item;
    }

    public Location getLocation() {
        return location;
    }

    public BigDecimal getRequiredQuantity() {
        return requiredQuantity;
    }

    public User getAssignedUser() {
        return assignedUser;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public void setStatus(WorkOrderStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void markCompletedAt(Instant when) {
        this.completedAt = when;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
