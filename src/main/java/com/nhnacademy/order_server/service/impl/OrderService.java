package com.nhnacademy.order_server.service.impl;


import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    Long createOrder(OrderCreateRequest request);
    Page<OrderResponse> getMyOrders(Long userId, Pageable pageable);
    OrderResponse getOrderDetail(Long orderId);
    OrderResponse getGuestOrder(Long orderId, Integer password);
    void cancelOrder(Long orderId);
    OrderReturnCheckResponse checkReturn(Long orderId);
    void requestReturn(Long orderId, OrderReturnRequest request);
}
