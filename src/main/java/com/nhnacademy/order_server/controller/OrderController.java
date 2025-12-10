package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.OrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.response.*;
import com.nhnacademy.order_server.service.OrderService;
import com.nhnacademy.order_server.service.WrapperService;
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
    private final WrapperService wrapperService;

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
    @GetMapping("/{orderKey}/payments")
    public ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(@PathVariable String orderKey) {
        return ResponseEntity.ok(orderService.getValidationInfo(orderKey));
    }

    @Override
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestHeader(value = "X-USER-ID") Long userId,
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
    @GetMapping("/wrappers")
    public ResponseEntity<List<WrapperResponse>> getWrappers() {
        return ResponseEntity.ok(wrapperService.getAvailableWrappers());
    }
}