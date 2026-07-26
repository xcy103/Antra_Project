package com.bookstore.orderservice.repository;

import com.bookstore.orderservice.entity.Order;
import com.bookstore.orderservice.entity.OrderItem;
import com.bookstore.orderservice.support.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests for orders against a real PostgreSQL: the order and its
 * cascaded items persist and load back together.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest extends AbstractPostgresIT {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    private Order orderWithItem(String owner, Long bookId, int qty, String price) {
        Order order = new Order(owner);
        order.addItem(new OrderItem(bookId, qty, new BigDecimal(price)));
        order.setTotalPrice(new BigDecimal(price).multiply(BigDecimal.valueOf(qty)));
        return order;
    }

    @Test
    void savesOrderWithCascadedItems() {
        Order saved = orderRepository.save(orderWithItem("alice", 1L, 2, "10.00"));
        entityManager.flush();
        entityManager.clear();

        Optional<Order> found = orderRepository.findByIdWithItems(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerUsername()).isEqualTo("alice");
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).getBookId()).isEqualTo(1L);
        assertThat(found.get().getTotalPrice()).isEqualByComparingTo("20.00");
    }

    @Test
    void findAllByOwner_returnsOnlyThatOwnersOrders() {
        orderRepository.save(orderWithItem("alice", 1L, 1, "10.00"));
        orderRepository.save(orderWithItem("bob", 2L, 1, "12.00"));
        entityManager.flush();

        assertThat(orderRepository.findAllByOwner("alice")).hasSize(1);
        assertThat(orderRepository.findAllByOwner("alice").get(0).getOwnerUsername()).isEqualTo("alice");
    }
}
