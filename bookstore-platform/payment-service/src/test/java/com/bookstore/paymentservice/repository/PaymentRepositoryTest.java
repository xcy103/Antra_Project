package com.bookstore.paymentservice.repository;

import com.bookstore.paymentservice.entity.Payment;
import com.bookstore.paymentservice.entity.PaymentStatus;
import com.bookstore.paymentservice.support.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest extends AbstractPostgresIT {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    private Payment payment(Long orderId) {
        return new Payment(orderId, "alice", new BigDecimal("29.99"), PaymentStatus.SUCCESS);
    }

    @Test
    void savesAndFindsByOrderId() {
        paymentRepository.save(payment(10L));
        entityManager.flush();

        assertThat(paymentRepository.findByOrderId(10L)).isPresent();
        assertThat(paymentRepository.existsByOrderId(10L)).isTrue();
        assertThat(paymentRepository.existsByOrderId(99L)).isFalse();
    }

    @Test
    void duplicateOrderPayment_violatesUniqueConstraint() {
        paymentRepository.saveAndFlush(payment(20L));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(20L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
