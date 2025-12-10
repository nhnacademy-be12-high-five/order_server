package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.AdminOrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminOrderController implements AdminOrderControllerDocs {

    // private final AdminOrderService adminOrderService; // 나중에 주입

    @Override
    public ResponseEntity<Page<OrderResponse>> getOrders(Pageable pageable, String status) {
        return ResponseEntity.ok(Page.empty());
    }

    @Override
    public ResponseEntity<Void> updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> processReturn(Long returnId, boolean isApproved) {
        return ResponseEntity.ok().build();
    }
}