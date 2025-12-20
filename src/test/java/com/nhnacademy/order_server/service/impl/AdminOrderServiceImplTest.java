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
import org.junit.jupiter.api.Nested;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

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

    @Nested
    @DisplayName("1. 주문 목록 조회")
    class GetOrdersTest {
        @Test
        @DisplayName("전체 조회 성공")
        void getOrders_All() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).build();
            given(orderRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));

            // when
            Page<OrderResponse> result = adminOrderService.getOrders(pageable, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findAll(pageable);
        }

        @Test
        @DisplayName("상태 필터링 조회 성공")
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
        @DisplayName("잘못된 상태값으로 조회 시 예외 발생")
        void getOrders_InvalidStatus() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            String invalidStatus = "UNKNOWN_STATUS";

            // when & then
            assertThatThrownBy(() -> adminOrderService.getOrders(pageable, invalidStatus))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("2. 주문 상태 변경")
    class UpdateOrderStatusTest {

        private OrderStatusUpdateRequest request;
        private Order order;
        private Delivery delivery;

        @Test
        @DisplayName("배송 중(DELIVERING) 변경 성공")
        void updateToDelivering_Success() {
            // given
            setUpOrder(DeliveryStatus.PREPARING);
            setUpRequest("DELIVERING", "1234567890");

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when
            adminOrderService.updateOrderStatus(1L, request);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERING);
            assertThat(order.getDelivery().getTrackingNumber()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("배송 중 변경 실패 - 송장 번호 누락")
        void updateToDelivering_Fail_NoTrackingNumber() {
            // given
            setUpOrder(DeliveryStatus.PREPARING);
            setUpRequest("DELIVERING", ""); // Blank

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> adminOrderService.updateOrderStatus(1L, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("배송 완료(DELIVERY_COMPLETED) 변경 성공")
        void updateToDeliveryCompleted_Success() {
            // given
            setUpOrder(DeliveryStatus.DELIVERING);
            setUpRequest("DELIVERY_COMPLETED", null);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when
            adminOrderService.updateOrderStatus(1L, request);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);
            // Delivery 엔티티의 completeDelivery()가 호출되었는지 간접 확인 (상태 변경 로직에 포함됨)
        }

        @Test
        @DisplayName("구매 확정(PURCHASE_CONFIRMED) 변경 성공")
        void updateToPurchaseConfirmed_Success() {
            // given
            setUpOrder(DeliveryStatus.DELIVERY_COMPLETED); // 배송 완료 상태여야 함
            setUpRequest("PURCHASE_CONFIRMED", null);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when
            adminOrderService.updateOrderStatus(1L, request);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PURCHASE_CONFIRMED);
        }

        @Test
        @DisplayName("구매 확정 변경 실패 - 배송 완료 상태가 아님")
        void updateToPurchaseConfirmed_Fail_InvalidStatus() {
            // given
            setUpOrder(DeliveryStatus.DELIVERING); // 아직 배송 중
            setUpRequest("PURCHASE_CONFIRMED", null);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> adminOrderService.updateOrderStatus(1L, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_REQUEST);
        }

        private void setUpOrder(DeliveryStatus status) {
            order = Order.builder().id(1L).deliveryStatus(status).build();
            delivery = Delivery.builder().order(order).build();
            ReflectionTestUtils.setField(order, "delivery", delivery);
        }

        private void setUpRequest(String status, String trackingNumber) {
            request = new OrderStatusUpdateRequest();
            ReflectionTestUtils.setField(request, "status", status);
            ReflectionTestUtils.setField(request, "trackingNumber", trackingNumber);
        }
    }

    @Nested
    @DisplayName("3. 반품 처리")
    class ProcessReturnTest {

        private Order order;
        private OrderReturn orderReturn;

        @Test
        @DisplayName("반품 승인 - 전체 성공 (포인트 환불 + 적립 회수 + PG 취소)")
        void processReturn_Approve_FullSuccess() {
            // given
            setUpReturn(DeliveryStatus.DELIVERY_COMPLETED, 1000, 500); // 1000원 사용, 500원 적립됨
            ReflectionTestUtils.setField(order, "paymentKey", "toss_key");

            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            // when
            adminOrderService.processReturn(1L, true);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURN_COMPLETED);

            // 1. 사용 포인트 환불 (orderId 포함 검증)
            verify(memberClient).cancelPoint(eq(100L), eq(1000), eq(1L));

            // 2. 적립 포인트 회수
            verify(memberClient).deductPoint(eq(100L), eq(500));

            // 3. PG 취소
            verify(paymentClient).cancelPayment(eq("toss_key"), any(PaymentCancelRequest.class));
        }

        @Test
        @DisplayName("반품 승인 실패 - 포인트 서버 오류")
        void processReturn_Approve_Fail_MemberService() {
            // given
            setUpReturn(DeliveryStatus.DELIVERY_COMPLETED, 1000, 0);
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            // 포인트 환불 시 예외 발생
            willThrow(new RuntimeException("Connection Refused"))
                    .given(memberClient).cancelPoint(anyLong(), anyInt(), anyLong());

            // when & then
            assertThatThrownBy(() -> adminOrderService.processReturn(1L, true))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.MEMBER_SERVICE_ERROR);
        }

        @Test
        @DisplayName("반품 승인 실패 - PG 서버 오류")
        void processReturn_Approve_Fail_PGService() {
            // given
            setUpReturn(DeliveryStatus.DELIVERY_COMPLETED, 0, 0); // 포인트 사용 X
            ReflectionTestUtils.setField(order, "paymentKey", "toss_key");
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            // PG 취소 시 예외 발생
            willThrow(new RuntimeException("PG Error"))
                    .given(paymentClient).cancelPayment(anyString(), any());

            // when & then
            assertThatThrownBy(() -> adminOrderService.processReturn(1L, true))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.EXTERNAL_API_ERROR);
        }

        @Test
        @DisplayName("반품 거절 - 상태 원복")
        void processReturn_Reject() {
            // given
            setUpReturn(DeliveryStatus.RETURN_REQUESTED, 0, 0);
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            // when
            adminOrderService.processReturn(1L, false);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);

            // 외부 서비스 호출 안됨 검증
            verify(memberClient, times(0)).cancelPoint(any(), any(), any());
            verify(paymentClient, times(0)).cancelPayment(any(), any());
        }

        private void setUpReturn(DeliveryStatus status, int usedPoint, int earnedPoint) {
            order = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .pointDiscount(usedPoint)
                    .earnedPoint(earnedPoint)
                    .deliveryStatus(status)
                    .paymentAmount(5000)
                    .build();

            orderReturn = OrderReturn.builder()
                    .order(order)
                    .refundAmount(5000)
                    .returnReason(ReturnReason.PRODUCT_DEFECT)
                    .build();
        }
    }
}