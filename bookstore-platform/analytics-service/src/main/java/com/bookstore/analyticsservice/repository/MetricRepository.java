package com.bookstore.analyticsservice.repository;

import com.bookstore.analyticsservice.entity.Metric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricRepository extends JpaRepository<Metric, String> {
}
