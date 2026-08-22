package com.minierp.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "customer_order_items")
public class CustomerOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    protected CustomerOrderItem() {
    }

    public CustomerOrderItem(CustomerOrder order, Item item, Location location, BigDecimal quantity) {
        this.order = order;
        this.item = item;
        this.location = location;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public Item getItem() {
        return item;
    }

    public Location getLocation() {
        return location;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
