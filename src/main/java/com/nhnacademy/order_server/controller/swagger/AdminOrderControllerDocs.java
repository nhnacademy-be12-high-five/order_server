package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Order API", description = "관리자 주문/반품 상태 관리")
@RequestMapping("/api/admin/orders")
public interface AdminOrderControllerDocs {

    @Operation(summary = "전체 주문 목록 조회", description = "관리자가 전체 주문 목록을 페이징하여 조회합니다. 상태값으로 필터링할 수 있습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효하지 않은 상태값 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    ResponseEntity<Page<OrderResponse>> getOrders(
            Pageable pageable,
            @Parameter(description = "주문 상태 필터 (예: WAITING, DELIVERING, COMPLETED)") @RequestParam(required = false) String status
    );

    @Operation(summary = "주문 상태 변경", description = "주문의 상태를 변경합니다. 배송 시작(DELIVERING) 시에는 송장 번호가 필수입니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (송장 번호 누락, 유효하지 않은 상태값)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{orderId}/status")
    ResponseEntity<Void> updateOrderStatus(
            @Parameter(description = "주문 ID") @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    );

    @Operation(summary = "반품 승인/거절 처리", description = "반품 요청을 승인하거나 거절합니다. 승인 시 포인트 및 결제 환불이 진행됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "주문(반품) 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "외부 시스템(포인트, 결제) 연동 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/returns/{returnId}/process")
    ResponseEntity<Void> processReturn(
            @Parameter(description = "반품 ID (주문 ID와 동일)") @PathVariable Long returnId,
            @Parameter(description = "승인 여부 (true: 승인, false: 거절)") @RequestParam boolean isApproved
    );
}