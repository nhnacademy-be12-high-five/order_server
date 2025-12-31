package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.CommonPageResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Admin Order API", description = "관리자 주문/반품 상태 관리")
@RequestMapping("/api/admin/orders")
public interface AdminOrderControllerDocs {

    @Operation(summary = "전체 주문 목록 조회", description = "관리자가 전체 주문 목록을 페이징하여 조회합니다. 상태값으로 필터링할 수 있습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    ResponseEntity<CommonPageResponse<OrderResponse>> getOrders(
            Pageable pageable,
            @Parameter(description = "주문 상태 필터") @RequestParam(required = false) String status
    );

    @Operation(summary = "주문 상태 변경")
    @PutMapping("/{orderId}/status")
    ResponseEntity<Void> updateOrderStatus(
            @Parameter(description = "주문 ID") @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    );

    @Operation(summary = "반품 승인/거절 처리")
    @PutMapping("/returns/{returnId}/process")
    ResponseEntity<Void> processReturn(
            @Parameter(description = "반품 ID") @PathVariable Long returnId,
            @Parameter(description = "승인 여부") @RequestParam boolean isApproved
    );
}