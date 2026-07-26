package com.bookstore.orderservice.service.impl;

import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.orderservice.client.BookClient;
import com.bookstore.orderservice.client.BookDto;
import com.bookstore.orderservice.dto.OrderItemRequest;
import com.bookstore.orderservice.messaging.OrderEventPublisher;
import com.bookstore.orderservice.dto.OrderResponse;
import com.bookstore.orderservice.dto.PlaceOrderRequest;
import com.bookstore.orderservice.entity.Order;
import com.bookstore.orderservice.entity.OrderItem;
import com.bookstore.orderservice.entity.OrderStatus;
import com.bookstore.orderservice.exception.ForbiddenOrderAccessException;
import com.bookstore.orderservice.exception.InsufficientStockException;
import com.bookstore.orderservice.exception.OrderStateException;
import com.bookstore.orderservice.repository.OrderRepository;
import com.bookstore.orderservice.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final BookClient bookClient;
    private final OrderEventPublisher orderEventPublisher;

    public OrderServiceImpl(OrderRepository orderRepository, BookClient bookClient,
                            OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.bookClient = bookClient;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(String owner, PlaceOrderRequest request) {
        Order order = new Order(owner);
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest line : request.items()) {
            // Feign call to book-service (circuit-breaker protected: on failure the
            // fallback throws CatalogUnavailableException -> 503, no cascading hang).
            BookDto book = bookClient.getBook(line.bookId());
            if (book.stock() < line.quantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for book " + line.bookId()
                                + " (requested " + line.quantity() + ", available " + book.stock() + ")");
            }
            order.addItem(new OrderItem(line.bookId(), line.quantity(), book.price()));
            total = total.add(book.price().multiply(BigDecimal.valueOf(line.quantity())));
        }

        order.setTotalPrice(total);
        Order saved = orderRepository.save(order);

        orderEventPublisher.publishOrderPlaced(new OrderPlacedEvent(
                UUID.randomUUID().toString(), saved.getId(), owner, saved.getTotalPrice(), Instant.now()));

        return OrderResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String owner) {
        return orderRepository.findAllByOwner(owner).stream().map(OrderResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllWithItems().stream().map(OrderResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id, String requester, boolean isAdmin) {
        return OrderResponse.from(loadAuthorized(id, requester, isAdmin));
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, String requester, boolean isAdmin) {
        Order order = loadAuthorized(id, requester, isAdmin);
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new OrderStateException("Cannot cancel an order that has already shipped");
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    private Order loadAuthorized(Long id, String requester, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + id));
        if (!isAdmin && !order.getOwnerUsername().equals(requester)) {
            throw new ForbiddenOrderAccessException("You do not have access to order " + id);
        }
        return order;
    }
}
