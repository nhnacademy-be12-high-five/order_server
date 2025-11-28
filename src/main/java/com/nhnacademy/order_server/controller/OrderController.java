package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.docs.OrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs  {

    private final OrderService orderService;

    @Override
    public ResponseEntity<Long> createOrder(OrderCreateRequest request) {
        Long orderId = orderService.createOrder(request);
        return ResponseEntity.status(201).body(orderId);
    }

    @Override
    public ResponseEntity<Page<OrderResponse>> getMyOrders(Long userId, Pageable pageable) {
        // 실제로는 userId를 헤더나 토큰에서 꺼내는 처리를 여기서 하기도 함
        return ResponseEntity.ok(orderService.getMyOrders(userId, pageable));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrderDetail(Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(orderId));
    }

    @Override
    public ResponseEntity<OrderResponse> getGuestOrder(OrderGuestLoginRequest request) {
        return ResponseEntity.ok(orderService.getGuestOrder(request.getOrderId(), request.getPassword()));
    }

    @Override
    public ResponseEntity<Void> cancelOrder(Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(Long orderId) {
        return ResponseEntity.ok(orderService.checkReturn(orderId));
    }

    @Override
    public ResponseEntity<Void> requestReturn(Long orderId, OrderReturnRequest request) {
        orderService.requestReturn(orderId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<List<WrapperResponse>> getWrappers() {
        return ResponseEntity.ok(List.of(
                WrapperResponse.builder().id(1L).name("크리스마스 포장").price(1000).build()
        ));
    }
}
