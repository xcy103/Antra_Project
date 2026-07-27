package com.bookstore.analyticsservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A running aggregate keyed by name (e.g. "orders", "payments"): how many events
 * and the summed amount.
 */
@Entity
@Table(name = "metric")
public class Metric {

    @Id
    private String name;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    protected Metric() {
    }

    public Metric(String name) {
        this.name = name;
        this.totalCount = 0L;
        this.totalAmount = BigDecimal.ZERO;
    }

    public void add(BigDecimal amount) {
        this.totalCount += 1;
        this.totalAmount = this.totalAmount.add(amount);
    }

    public String getName() {
        return name;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
