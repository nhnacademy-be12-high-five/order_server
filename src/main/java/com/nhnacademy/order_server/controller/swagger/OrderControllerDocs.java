package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.*;
import com.nhnacademy.order_server.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Order", description = "주문, 결제, 조회 및 반품 관련 API")
public interface OrderControllerDocs {

    @Operation(summary = "주문 생성 (결제 진입)", description = "주문 정보를 검증하고 PENDING 상태의 주문을 생성합니다. 반환된 주문 ID와 Key로 결제창을 호출하세요.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (재고 부족, 포인트 부족, 비밀번호 누락 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request);

  /*  @Operation(summary = "결제 완료 처리 (최종 확정)", description = "PG사 결제 성공 후 호출하여 재고와 포인트를 확정하고 주문 상태를 WAITING으로 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 주문"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류 (재고 확정 실패 등)")
    })
    ResponseEntity<Void> paymentSuccess(
            @Parameter(description = "주문 번호") Long orderId,
            @Parameter(description = "결제 키 (Payment Key)") String paymentKey);*/

    @Operation(summary = "결제 검증 데이터 조회", description = "결제 서버가 승인 요청 전, 주문 금액의 위변조 여부를 확인하기 위해 호출합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = OrderValidationInfoResponse.class))),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(
            @Parameter(description = "주문 고유 키 (UUID)") String orderKey); // [수정] 타입 및 설명 변경


    @Operation(summary = "회원 주문 목록 조회", description = "로그인한 회원의 주문 내역을 페이지네이션하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자 (X-USER-ID 헤더 누락)")
    })
    ResponseEntity<CommonPageResponse<OrderResponse>> getMyOrders(
            @Parameter(description = "회원 식별 ID (Gateway에서 주입)", required = true) Long userId,
            @Parameter(description = "페이징 정보 (page=0, size=10, sort=id,desc 등)") Pageable pageable);

    @Operation(summary = "주문 상세 조회", description = "주문 번호로 상세 내역(주문 상품 포함)을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderResponse> getOrderDetail(@Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "비회원 주문 조회 (로그인)", description = "주문 번호와 비밀번호를 검증하여 비회원 주문 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "인증 및 조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문 번호가 없거나 비밀번호가 일치하지 않음 (보안상 404로 통일)")
    })
    ResponseEntity<OrderResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request);

    @Operation(summary = "포장지 목록 조회", description = "주문 시 선택 가능한 포장지 옵션을 조회합니다.")
    ResponseEntity<List<WrapperResponse>> getWrappers();

    @Operation(summary = "현재 배송 정책 조회", description = "현재 적용 중인 기본 배송비와 무료 배송 기준 금액을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<DeliveryPolicyResponse> getCurrentDeliveryPolicy();

    @Operation(summary = "주문 취소 (PENDING 상태)", description = "결제 대기 중인 주문을 취소하고 재고 및 포인트 예약을 해제합니다.")
    ResponseEntity<Void> cancelOrder(
            @Parameter(description = "주문 ID", required = true) @PathVariable Long orderId);
}