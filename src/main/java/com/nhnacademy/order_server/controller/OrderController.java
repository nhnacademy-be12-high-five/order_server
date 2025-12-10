package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.docs.OrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.*;
import com.nhnacademy.order_server.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final OrderService orderService;

    @Override
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderCreateResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PostMapping("/{orderId}/payments")
    public ResponseEntity<Void> paymentSuccess(
            @PathVariable Long orderId,
            @RequestParam String paymentKey) {
        orderService.paymentSuccess(orderId, paymentKey);
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/{orderId}/payments")
    public ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getValidationInfo(orderId));
    }

    @Override
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestHeader(value = "X-USER-ID", required = false) Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getMyOrders(userId, pageable));
    }


    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(orderId));
    }

    @Override
    @PostMapping("/guests/search")
    public ResponseEntity<OrderResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request) {
        return ResponseEntity.ok(orderService.getGuestOrder(request.getOrderId(), request.getPassword()));
    }

    @Override
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }


    @Override
    @GetMapping("/{orderId}/returns/eligibility")
    public ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.checkReturn(orderId));
    }


    @Override
    @PostMapping("/{orderId}/returns")
    public ResponseEntity<Void> requestReturn(@PathVariable Long orderId, @RequestBody OrderReturnRequest request) {
        orderService.requestReturn(orderId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/wrappers")
    public ResponseEntity<List<WrapperResponse>> getWrappers() {
        return ResponseEntity.ok(List.of(
                WrapperResponse.builder().id(1L).name("일반 포장").price(1000).build()
        ));
    }
}