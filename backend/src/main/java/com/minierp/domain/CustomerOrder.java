package com.minierp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer sales order. Creating the order reserves stock for every line in one
 * transaction. Status currently holds CREATED; CANCELLED exists so cancellation
 * (releasing reservations) can be added without schema churn (docs/FUTURE-CHANGES.md).
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 24)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerOrderStatus status = CustomerOrderStatus.CREATED;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNumber, String customerName, User createdBy) {
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.createdBy = createdBy;
    }

    public void addItem(CustomerOrderItem line) {
        items.add(line);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public CustomerOrderStatus getStatus() {
        return status;
    }

    public List<CustomerOrderItem> getItems() {
        return items;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
