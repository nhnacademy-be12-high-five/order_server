package com.nhnacademy.order_server.controller.docs;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
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

@Tag(name ="Admin Order API", description = "관리자용 주문/반품/정책 관리 API")
@RequestMapping("/api/admin")
public interface AdminOrderControllerDocs {

    @Operation(summary = "전체 주문 목록 조회", description = "상태값으로 필터링하여 주문 목록을 조회합니다.")
    @GetMapping("/orders")
    ResponseEntity<Page<OrderResponse>> getOrders(
            Pageable pageable,
            @Parameter(description = "주문 상태 필터 (WAITING, DELIVERING 등)", example = "WAITING")
            @RequestParam(required = false) String status
    );

    @Operation(summary = "주문 상태 변경", description = "특정 주문의 상태를 변경합니다. (예: 배송중 처리)")
    @PutMapping("/orders/{orderId}/status")
    ResponseEntity<Void> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    );

    @Operation(summary = "반품 승인/거절 처리", description = "접수된 반품 건을 승인하거나 거절합니다.")
    @PutMapping("/returns/{returnId}/process")
    ResponseEntity<Void> processReturn(
            @PathVariable Long returnId,
            @Parameter(description = "승인 여부 (true: 승인, false: 거절)") @RequestParam boolean isApproved
    );

    @Operation(summary = "배송 정책 등록", description = "새로운 배송비 정책을 등록합니다.")
    @PostMapping("/delivery-policies")
    ResponseEntity<Void> createDeliveryPolicy(@Valid @RequestBody DeliveryPolicyRequest request);

    @Operation(summary = "배송 정책 삭제", description = "기존 배송비 정책을 삭제합니다.")
    @DeleteMapping("/delivery-policies/{policyId}")
    ResponseEntity<Void> deleteDeliveryPolicy(@PathVariable Long policyId);
}
