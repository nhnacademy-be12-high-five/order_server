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

@Tag(name = "Order", description = "주문 및 결제 관련 API")
public interface OrderControllerDocs {

    @Operation(summary = "주문 생성 (결제 요청 준비)", description = "최종 결제 전, 결제 대기(PENDING) 상태의 주문을 생성하고 결제창 호출에 필요한 정보(주문번호, 금액 등)를 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "주문 생성 성공",
                    content = @Content(schema = @Schema(implementation = OrderCreateResponse.class))), // [수정] 반환 타입 변경
            @ApiResponse(responseCode = "400", description = "요청 오류 (재고 부족, 포인트 부족, 유효성 검증 실패)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류 (외부 MSA 통신 오류)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request);

    @Operation(summary = "결제 성공 통보 (Callback)", description = "결제 서버로부터 결제 승인 완료 신호를 받아 주문 상태를 WAITING으로 변경하고 후처리를 수행합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "404", description = "주문 번호를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 주문"),
            @ApiResponse(responseCode = "500", description = "후처리 실패 (외부 서비스 오류)")
    })
    ResponseEntity<Void> paymentSuccess(
            @Parameter(description = "주문 번호") Long orderId,
            @Parameter(description = "결제 서버에서 받은 결제 고유 키") String paymentKey);

    @Operation(summary = "결제 검증 데이터 조회 (Internal)", description = "[내부용] 결제 서버가 승인 요청 전, 주문 금액의 위변조 여부를 확인하기 위해 호출합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = OrderValidationInfoResponse.class))),
            @ApiResponse(responseCode = "404", description = "주문 번호를 찾을 수 없음")
    })
    ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "회원 주문 목록 조회", description = "로그인된 회원의 전체 주문 내역을 페이지네이션하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "주문 목록 조회 성공")
    })
    ResponseEntity<Page<OrderResponse>> getMyOrders(
            @Parameter(description = "회원 ID (Header X-USER-ID)") Long userId,
            Pageable pageable);

    @Operation(summary = "주문 상세 내역 조회", description = "특정 주문 번호의 상세 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "상세 내역 조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<OrderResponse> getOrderDetail(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "비회원 주문 조회", description = "주문 번호와 비밀번호를 사용하여 비회원 주문 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "주문 번호를 찾을 수 없거나 비밀번호가 틀림")
    })
    ResponseEntity<OrderResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request);

    @Operation(summary = "주문 취소 요청", description = "배송 시작 전의 주문을 취소하고 결제 서버에 환불을 요청합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "취소 요청 및 처리 성공"),
            @ApiResponse(responseCode = "400", description = "배송이 시작되어 취소 불가능"),
            @ApiResponse(responseCode = "404", description = "주문 번호를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "결제 서버 환불 요청 실패")
    })
    ResponseEntity<Void> cancelOrder(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "반품 가능 여부 확인", description = "주문 번호를 기반으로 현재 상태에서 반품이 가능한지 기한을 확인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확인 성공"),
            @ApiResponse(responseCode = "404", description = "주문 번호를 찾을 수 없음")
    })
    ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(
            @Parameter(description = "주문 번호") Long orderId);

    @Operation(summary = "반품 요청", description = "배송 완료된 주문에 대해 반품 처리를 요청합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "반품 요청 성공"),
            @ApiResponse(responseCode = "400", description = "반품 기한 만료 또는 상태 오류")
    })
    ResponseEntity<Void> requestReturn(
            @Parameter(description = "주문 번호") Long orderId,
            @RequestBody OrderReturnRequest request);

    @Operation(summary = "사용 가능한 포장지 목록 조회", description = "현재 활성화된 모든 포장지 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "목록 조회 성공")
    })
    ResponseEntity<List<WrapperResponse>> getWrappers();
}