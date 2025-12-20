package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.adapter.PaymentClient;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderReturn;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.entity.enums.ReturnReason;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.OrderReturnRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @InjectMocks
    private AdminOrderServiceImpl adminOrderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private MemberClient memberClient;

    @Mock
    private PaymentClient paymentClient;

    @Test
    @DisplayName("주문 목록 조회 - 전체 조회")
    void getOrders_All() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        // [수정] PENDING -> PAYMENT_WAITING
        Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).build();
        given(orderRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));

        // when
        Page<OrderResponse> result = adminOrderService.getOrders(pageable, null);

        // then
        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository).findAll(pageable);
    }

    @Test
    @DisplayName("주문 목록 조회 - 상태 필터링")
    void getOrders_WithStatus() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        String status = "DELIVERING";
        Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.DELIVERING).build();
        given(orderRepository.findByDeliveryStatus(DeliveryStatus.DELIVERING, pageable))
                .willReturn(new PageImpl<>(List.of(order)));

        // when
        Page<OrderResponse> result = adminOrderService.getOrders(pageable, status);

        // then
        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository).findByDeliveryStatus(DeliveryStatus.DELIVERING, pageable);
    }

    @Test
    @DisplayName("주문 상태 변경 - 배송 중 (성공)")
    void updateOrderStatus_Delivering_Success() {
        // given
        Long orderId = 1L;
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", "DELIVERING");
        ReflectionTestUtils.setField(request, "trackingNumber", "1234567890");

        // [수정] WAITING -> PREPARING (배송 준비 중에서 배송 중으로 변경)
        Order order = Order.builder().id(orderId).deliveryStatus(DeliveryStatus.PREPARING).build();
        Delivery delivery = Delivery.builder().order(order).build();
        ReflectionTestUtils.setField(order, "delivery", delivery);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        adminOrderService.updateOrderStatus(orderId, request);

        // then
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERING);
        assertThat(order.getDelivery().getTrackingNumber()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("주문 상태 변경 - 배송 중인데 송장 번호 없음 (실패)")
    void updateOrderStatus_Delivering_NoTrackingNumber() {
        // given
        Long orderId = 1L;
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", "DELIVERING");
        // trackingNumber is null

        // [수정] WAITING -> PREPARING
        Order order = Order.builder().id(orderId).deliveryStatus(DeliveryStatus.PREPARING).build();
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> adminOrderService.updateOrderStatus(orderId, request))
                .isInstanceOf(OrderException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("반품 승인 - 포인트 및 결제 환불")
    void processReturn_Approve() {
        // given
        Long returnId = 1L;
        Long userId = 100L;
        int pointDiscount = 1000;
        int refundAmount = 5000;
        String paymentKey = "toss_key";

        Order order = Order.builder()
                .id(returnId)
                .userId(userId)
                .pointDiscount(pointDiscount)
                .paymentKey(paymentKey)
                // [수정] COMPLETED -> DELIVERY_COMPLETED
                .deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED)
                .build();

        OrderReturn orderReturn = OrderReturn.builder()
                .order(order)
                .refundAmount(refundAmount)
                .returnReason(ReturnReason.PRODUCT_DEFECT)
                .build();

        given(orderReturnRepository.findByIdWithOrder(returnId)).willReturn(Optional.of(orderReturn));

        // when
        adminOrderService.processReturn(returnId, true);

        // then
        // [수정] RETURN -> RETURN_COMPLETED (반품 완료)
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURN_COMPLETED);

        // 포인트 환불 검증 (userId, amount, orderId)
        verify(memberClient).cancelPoint(userId, pointDiscount, returnId);

        // 결제 취소 검증
        verify(paymentClient).cancelPayment(eq(paymentKey), any(PaymentCancelRequest.class));
    }

    @Test
    @DisplayName("반품 거절 - 상태 원복")
    void processReturn_Reject() {
        // given
        Long returnId = 1L;
        Order order = Order.builder().id(returnId).deliveryStatus(DeliveryStatus.RETURN_REQUESTED).build();
        OrderReturn orderReturn = OrderReturn.builder().order(order).build();

        given(orderReturnRepository.findByIdWithOrder(returnId)).willReturn(Optional.of(orderReturn));

        // when
        adminOrderService.processReturn(returnId, false);

        // then
        // [수정] COMPLETED -> DELIVERY_COMPLETED (배송 완료 상태로 원복)
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);

        // 호출되지 않음 검증 (인자 3개 확인)
        verify(memberClient, times(0)).cancelPoint(any(), any(), any());
        verify(paymentClient, times(0)).cancelPayment(any(), any());
    }
}