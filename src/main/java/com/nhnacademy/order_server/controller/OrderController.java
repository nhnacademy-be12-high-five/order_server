package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.OrderControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.response.CommonPageResponse;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.dto.response.GuestOrderDetailResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import com.nhnacademy.order_server.service.OrderService;
import com.nhnacademy.order_server.service.WrapperService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final OrderService orderService;
    private final WrapperService wrapperService;
    private final DeliveryPolicyService deliveryPolicyService;

    @Override
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderCreateResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/{orderKey}/payments")
    public ResponseEntity<OrderValidationInfoResponse> getPaymentInfo(@PathVariable String orderKey) {
        return ResponseEntity.ok(orderService.getValidationInfo(orderKey));
    }

    @Override
    @GetMapping
    public ResponseEntity<CommonPageResponse<OrderResponse>> getMyOrders(
            @RequestHeader(value = "X-USER-ID") Long userId,
            Pageable pageable) {

        Page<OrderResponse> page = orderService.getMyOrders(userId, pageable);

        return ResponseEntity.ok(new CommonPageResponse<>(page));
    }

    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(orderId));
    }

    @Override
    @GetMapping("/recent")
    public ResponseEntity<CommonPageResponse<OrderResponse>> getRecentOrders(
            @RequestHeader("X-USER-ID") Long userId,
            Pageable pageable
    ) {
        Page<OrderResponse> page = orderService.getMyOrdersLast3Months(userId, pageable);
        return ResponseEntity.ok(new CommonPageResponse<>(page));
    }

    @Override
    @PostMapping("/guests/search")
    public ResponseEntity<GuestOrderDetailResponse> getGuestOrder(@RequestBody OrderGuestLoginRequest request) {
        return ResponseEntity.ok(orderService.getGuestOrder(request.getOrderId(), request.getPassword()));
    }

    @Override
    @GetMapping("/wrappers")
    public ResponseEntity<List<WrapperResponse>> getWrappers() {
        return ResponseEntity.ok(wrapperService.getAvailableWrappers());
    }

    @Override
    @GetMapping("/policy/current")
    public ResponseEntity<DeliveryPolicyResponse> getCurrentDeliveryPolicy() {
        return ResponseEntity.ok(deliveryPolicyService.getActivePolicy());
    }

    @Override
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("{orderId}/confirm")
    public ResponseEntity<Void> confirmOrder(@PathVariable Long orderId) {
        orderService.purchaseConfirm(orderId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-purchase")
    public ResponseEntity<Boolean> hasPurchasedBook(@RequestParam("memberId") Long memberId,
                                                    @RequestParam("bookId") Long bookId) {
        Boolean result = orderService.hasPurchasedBook(memberId, bookId);
        return ResponseEntity.ok(result);
    }
}