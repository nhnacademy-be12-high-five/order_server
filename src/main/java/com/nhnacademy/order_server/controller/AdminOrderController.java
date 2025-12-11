package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.AdminOrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminOrderController implements AdminOrderControllerDocs {

    private final AdminOrderService adminOrderService;

    @Override
    public ResponseEntity<Page<OrderResponse>> getOrders(Pageable pageable, String status) {
        Page<OrderResponse> orders = adminOrderService.getOrders(pageable, status);
        return ResponseEntity.ok(orders);
    }

    @Override
    public ResponseEntity<Void> updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        adminOrderService.updateOrderStatus(orderId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> processReturn(Long returnId, boolean isApproved) {
        adminOrderService.processReturn(returnId, isApproved);
        return ResponseEntity.ok().build();
    }
}