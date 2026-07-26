package com.bookstore.orderservice.service;

import com.bookstore.orderservice.dto.OrderResponse;
import com.bookstore.orderservice.dto.PlaceOrderRequest;

import java.util.List;

public interface OrderService {

    OrderResponse placeOrder(String owner, PlaceOrderRequest request);

    List<OrderResponse> getMyOrders(String owner);

    List<OrderResponse> getAllOrders();

    OrderResponse getOrder(Long id, String requester, boolean isAdmin);

    OrderResponse cancelOrder(Long id, String requester, boolean isAdmin);
}
