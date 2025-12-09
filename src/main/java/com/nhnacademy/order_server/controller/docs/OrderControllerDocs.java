package com.nhnacademy.order_server.controller.docs;

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

import java.util.List;

@Tag(name = "Order", description = "주문 및 결제, 반품 관련 API")
public interface OrderControllerDocs {

    @Operation(summary = "주문 생성 (결제 요청 준비)", description = "주문 정보를 받아 검증(재고, 포인트, 쿠폰) 후 PENDING 상태의 주문을 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "400", description = "생성 실패 (원인: 비회원 비밀번호 누락, 재고 부족, 포인트 잔액 부족, 존재하지 않는 포장지 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류 (원인: 회원 등급 조회 실패, 배송비 계산 오류, 외부 서비스 연동 실패)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request);

    @Operation(summary = "결제 완료 처리 (리소스 확정)", description = "결제 서버 승인 후 호출되어, 주문 상태를 WAITING으로 변경하고 재고/포인트/쿠폰 사용을 확정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 주문 (ALREADY_PROCESSED)"),
            @ApiResponse(responseCode = "500", description = "후처리 실패 (재고 확정 실패 등 - 수동 확인 필요)")
    })
    ResponseEntity<Void> paymentSuccess(
            @Parameter(description = "주문 번호") Long orderId,
            @Parameter(description = "결제 서버에서 받은 결제 고유 키") String paymentKey);

    @Operation(summary = "결제 검증 데이터 조회", description = "결제 서버가 금액 위변조를 확인하기 위해 호출하는 내부용 API입니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "회원 주문 목록 조회", description = "로그인된 회원의 주문 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<Page<OrderResponse>> getMyOrders(
            @Parameter(description = "회원 ID (Header: X-USER-ID)") Long userId,
            Pageable pageable);

    @Operation(summary = "주문 상세 조회", description = "주문 번호에 해당하는 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderResponse> getOrderDetail(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "비회원 주문 조회 (검색)", description = "주문 번호와 비밀번호(검증)를 통해 비회원 주문을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "비밀번호 불일치"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request);

    @Operation(summary = "주문 취소", description = "배송 시작 전 상태의 주문을 취소하고(Soft Delete), 결제 취소 프로세스를 시작합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "400", description = "취소 불가 (원인: 이미 배송 시작됨, 이미 취소됨)"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<Void> cancelOrder(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "반품 가능 여부 확인", description = "해당 주문이 현재 반품 가능한 상태인지(기간, 상태 등) 확인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확인 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "반품 신청", description = "반품 사유와 함께 반품을 신청합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "신청 성공"),
            @ApiResponse(responseCode = "400", description = "신청 실패 (원인: 반품 기한 만료, 반품 불가 상태, 이미 반품 진행 중)"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<Void> requestReturn(
            @Parameter(description = "주문 번호") Long orderId,
            @RequestBody OrderReturnRequest request);

    @Operation(summary = "포장지 목록 조회", description = "주문 시 선택 가능한 포장지 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<List<WrapperResponse>> getWrappers();
}