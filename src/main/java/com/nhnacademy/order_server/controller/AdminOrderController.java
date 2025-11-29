package com.nhnacademy.order_server.controller;

import org.springframework.web.bind.annotation.RestController;

import com.nhnacademy.order_server.controller.docs.AdminOrderControllerDocs;
import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@RestController
@RequiredArgsConstructor
public class AdminOrderController implements AdminOrderControllerDocs {

    private final AdminOrderService adminOrderService;

    @Override
    public ResponseEntity<Page<OrderResponse>> getOrders(Pageable pageable, String status) {
        return ResponseEntity.ok(adminOrderService.getOrders(pageable, status));
    }

    @Override
    public ResponseEntity<Void> updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        adminOrderService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> processReturn(Long returnId, boolean isApproved) {
        adminOrderService.processReturn(returnId, isApproved);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> createDeliveryPolicy(DeliveryPolicyRequest request) {
        adminOrderService.createDeliveryPolicy(request);
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<Void> deleteDeliveryPolicy(Long policyId) {
        adminOrderService.deleteDeliveryPolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}