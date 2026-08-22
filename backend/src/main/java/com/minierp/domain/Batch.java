package com.minierp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "batches")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "batch_code", nullable = false, length = 40)
    private String batchCode;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Batch() {
    }

    public Batch(Item item, String batchCode) {
        this.item = item;
        this.batchCode = batchCode;
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
