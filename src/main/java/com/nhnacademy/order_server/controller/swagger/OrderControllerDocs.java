package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.response.CommonPageResponse;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.dto.response.GuestOrderDetailResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Order", description = "주문, 결제, 조회 및 반품 관련 API")
public interface OrderControllerDocs {

    @Operation(summary = "주문 생성 (결제 진입)", description = "주문 정보를 검증하고 PAYMENT_WAITING 상태의 주문을 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (재고/포인트 부족 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request);

    @Operation(summary = "결제 검증 데이터 조회", description = "결제창 호출 전, 주문 데이터(금액, 키 등)를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(
            @Parameter(description = "주문 고유 키 (UUID)") String orderKey);

    @Operation(summary = "회원 주문 목록 조회", description = "로그인한 회원의 전체 주문 내역을 페이지네이션하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<CommonPageResponse<OrderResponse>> getMyOrders(
            @Parameter(description = "회원 ID (Header)") Long userId,
            @Parameter(description = "페이징 정보") Pageable pageable);

    @Operation(summary = "최근 3개월 주문 내역 조회", description = "회원의 최근 3개월(90일)간 구매 확정된 주문 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<CommonPageResponse<OrderResponse>> getRecentOrders(
            @Parameter(description = "회원 ID (Header)") Long userId,
            @Parameter(description = "페이징 정보") Pageable pageable);

    @Operation(summary = "주문 상세 조회", description = "주문 ID로 상세 내역(주문 상품 포함)을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderResponse> getOrderDetail(@Parameter(description = "주문 ID") Long orderId);

    @Operation(summary = "비회원 주문 조회 (로그인)", description = "주문 번호와 비밀번호로 비회원 주문을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "일치하는 주문 없음")
    })
    ResponseEntity<GuestOrderDetailResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request);

    @Operation(summary = "포장지 목록 조회", description = "선택 가능한 모든 포장지 옵션을 조회합니다.")
    ResponseEntity<List<WrapperResponse>> getWrappers();

    @Operation(summary = "현재 배송 정책 조회", description = "현재 적용 중인 배송비 정책을 조회합니다.")
    ResponseEntity<DeliveryPolicyResponse> getCurrentDeliveryPolicy();

    @Operation(summary = "주문 취소", description = "결제 대기 또는 배송 준비 중인 주문을 취소합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "취소 성공"),
            @ApiResponse(responseCode = "400", description = "취소 불가능한 상태")
    })
    ResponseEntity<Void> cancelOrder(@Parameter(description = "주문 ID") Long orderId);
}