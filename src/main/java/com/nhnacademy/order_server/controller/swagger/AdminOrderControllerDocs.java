package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Order API", description = "관리자 주문/반품 상태 관리")
@RequestMapping("/api/admin/orders")
public interface AdminOrderControllerDocs {

    @Operation(summary = "전체 주문 목록 조회")
    @GetMapping
    ResponseEntity<Page<OrderResponse>> getOrders(
            Pageable pageable,
            @Parameter(description = "상태 필터") @RequestParam(required = false) String status
    );

    @Operation(summary = "주문 상태 변경 (배송 처리)")
    @PutMapping("/{orderId}/status")
    ResponseEntity<Void> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    );

    @Operation(summary = "반품 승인/거절 처리")
    @PutMapping("/returns/{returnId}/process")
    ResponseEntity<Void> processReturn(
            @PathVariable Long returnId,
            @RequestParam boolean isApproved
    );
}