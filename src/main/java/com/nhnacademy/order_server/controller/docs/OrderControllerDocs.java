package com.nhnacademy.order_server.controller.docs;

import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name ="Order API", description = "사용자 주문/취소/반품 관련 API")
@RequestMapping("/api/orders")
public interface OrderControllerDocs {

    @Operation(summary = "주문 생성", description = "회원 및 비회원 주문을 생성합니다.")
    @PostMapping
    ResponseEntity<Long> createOrder(@Valid @RequestBody OrderCreateRequest request);

    @Operation(summary = "내 주문 목록 조회(회원)",description = "로그인한 회원의 주문 목록을 페이징하여 조회합니다.")
    @GetMapping
    ResponseEntity<Page<OrderResponse>> getMyOrders(
            @Parameter(description = "회원 ID", example = "1") @RequestParam Long userId,
            Pageable pageable
    );

    @Operation(summary = "주문 상세 조회", description = "주문 ID로 상세 정보를 조회합니다.")
    @GetMapping("/{orderId}")
    ResponseEntity<OrderResponse> getOrderDetail(@PathVariable Long orderId);

    @Operation(summary = "비회원 주문 조회", description = "주문번호와 비밀번호로 비회원 주문을 조회합니다.")
    @PostMapping("/guest")
    ResponseEntity<OrderResponse> getGuestOrder(@Valid @RequestBody OrderGuestLoginRequest request);

    @Operation(summary = "주문 취소", description = "배송 전 상태일 경우 주문을 취소합니다.")
    @PostMapping("/{orderId}/cancel")
    ResponseEntity<Void> cancelOrder(@PathVariable Long orderId);

    @Operation(summary = "반품 가능 여부 확인", description = "반품 기한(10일/30일) 및 반품비를 계산하여 알려줍니다.")
    @GetMapping("/{orderId}/return-check")
    ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(@PathVariable Long orderId);

    @Operation(summary = "반품 신청", description = "반품 사유를 입력하여 반품을 접수합니다.")
    @PostMapping("/{orderId}/return")
    ResponseEntity<Void> requestReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderReturnRequest request
    );

    @Operation(summary = "포장지 목록 조회", description = "주문 시 선택 가능한 포장지 목록을 반환합니다.")
    @GetMapping("/wrappers")
    ResponseEntity<List<WrapperResponse>> getWrappers();
}
