package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.AdminOrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.CommonPageResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.service.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController implements AdminOrderControllerDocs {

    private final AdminOrderService adminOrderService;

    @Override
    @GetMapping
    public ResponseEntity<CommonPageResponse<OrderResponse>> getOrders(
            @PageableDefault(sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String status
    ) {
        Page<OrderResponse> orders = adminOrderService.getOrders(pageable, status);
        return ResponseEntity.ok(CommonPageResponse.from(orders));
    }

    @Override
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        adminOrderService.updateOrderStatus(orderId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PutMapping("/returns/{returnId}/process")
    public ResponseEntity<Void> processReturn(
            @PathVariable Long returnId,
            @RequestParam boolean isApproved
    ) {
        adminOrderService.processReturn(returnId, isApproved);
        return ResponseEntity.ok().build();
    }
}