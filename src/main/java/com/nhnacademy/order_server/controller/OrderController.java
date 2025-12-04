package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.docs.OrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.*;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders") // 공통 경로 설정
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final OrderService orderService;

    @Override
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        // 서비스에서 DTO를 반환하도록 수정된 메서드 호출
        OrderCreateResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PostMapping("/{orderId}/payment-success")
    public ResponseEntity<Void> paymentSuccess(
            @PathVariable Long orderId,
            @RequestParam String paymentKey) {
        orderService.paymentSuccess(orderId, paymentKey);
        return ResponseEntity.ok().build();
    }


    @Override
    @GetMapping("/{orderId}/payment-info")
    public ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getValidationInfo(orderId));
    }

    // --- 조회 및 기타 기능 ---

    @Override
    @GetMapping("/my")
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
    @PostMapping("/guest")
    public ResponseEntity<OrderResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request) {
        return ResponseEntity.ok(orderService.getGuestOrder(request.getOrderId(), request.getPassword()));
    }

    @Override
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/{orderId}/return-check")
    public ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.checkReturn(orderId));
    }

    @Override
    @PostMapping("/{orderId}/return")
    public ResponseEntity<Void> requestReturn(@PathVariable Long orderId, @RequestBody OrderReturnRequest request) {
        orderService.requestReturn(orderId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/wrappers")
    public ResponseEntity<List<WrapperResponse>> getWrappers() {
        // 실제로는 WrapperService를 호출해서 DB 데이터를 가져와야 함
        // 임시 Mock 데이터
        return ResponseEntity.ok(List.of(
                WrapperResponse.builder().id(1L).name("일반 포장").price(1000).build()
        ));
    }
}