package com.bookstore.orderservice.repository;

import com.bookstore.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select distinct o from Order o left join fetch o.items where o.ownerUsername = :owner")
    List<Order> findAllByOwner(String owner);

    @Query("select distinct o from Order o left join fetch o.items")
    List<Order> findAllWithItems();

    @Query("select o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(Long id);
}
