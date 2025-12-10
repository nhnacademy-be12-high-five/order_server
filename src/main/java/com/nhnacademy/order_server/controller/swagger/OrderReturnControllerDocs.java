package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Order Return", description = "주문 반품 및 환불 관련 API")
public interface OrderReturnControllerDocs {

    @Operation(summary = "반품 가능 여부 확인", description = "주문의 배송 상태와 출고일(기한)을 확인하여 반품 가능 여부와 예상 환불 금액을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확인 성공 (반품 가능/불가 여부 포함)"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(
            @Parameter(description = "주문 번호") @PathVariable Long orderId);

    @Operation(summary = "반품 신청", description = "배송 완료된 주문에 대해 반품을 접수합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "반품 접수 성공"),
            @ApiResponse(responseCode = "400", description = "반품 불가 (기한 만료, 이미 접수됨 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> requestReturn(
            @Parameter(description = "주문 번호") @PathVariable Long orderId,
            @RequestBody OrderReturnRequest request);
}