package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.request.PointEarnRequest;
import com.nhnacademy.order_server.dto.request.PointTransactionRequest;
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
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
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

    @Nested
    @DisplayName("1. 주문 목록 조회")
    class GetOrdersTest {
        @Test
        @DisplayName("전체 조회 성공")
        void getOrders_All() {
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.PAYMENT_WAITING).build();
            given(orderRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));

            Page<OrderResponse> result = adminOrderService.getOrders(pageable, null);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findAll(pageable);
        }

        @Test
        @DisplayName("상태 필터링 조회 성공")
        void getOrders_WithStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            String status = "DELIVERING";
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.DELIVERING).build();
            given(orderRepository.findByDeliveryStatus(DeliveryStatus.DELIVERING, pageable))
                    .willReturn(new PageImpl<>(List.of(order)));

            Page<OrderResponse> result = adminOrderService.getOrders(pageable, status);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findByDeliveryStatus(DeliveryStatus.DELIVERING, pageable);
        }
    }

    @Nested
    @DisplayName("2. 주문 상태 변경")
    class UpdateOrderStatusTest {

        private OrderStatusUpdateRequest request;
        private Order order;

        @Test
        @DisplayName("배송 중(DELIVERING) 변경 성공")
        void updateToDelivering_Success() {
            setUpOrder(DeliveryStatus.PREPARING);
            setUpRequest("DELIVERING", "TRACK123");
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            adminOrderService.updateOrderStatus(1L, request);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERING);
            assertThat(order.getDelivery().getTrackingNumber()).isEqualTo("TRACK123");
        }

        @Test
        @DisplayName("구매 확정(PURCHASE_CONFIRMED) 변경 성공 - 배송 완료 상태일 때")
        void updateToPurchaseConfirmed_Success() {
            setUpOrder(DeliveryStatus.DELIVERY_COMPLETED);
            setUpRequest("PURCHASE_CONFIRMED", null);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            adminOrderService.updateOrderStatus(1L, request);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PURCHASE_CONFIRMED);
        }

        @Test
        @DisplayName("반품 완료(RETURN_COMPLETED) 변경 성공")
        void updateToReturnCompleted_Success() {
            setUpOrder(DeliveryStatus.DELIVERY_COMPLETED);
            setUpRequest("RETURN_COMPLETED", null);
            OrderReturn orderReturn = OrderReturn.builder().order(order).refundAmount(5000).build();

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderReturnRepository.findByOrderId(1L)).willReturn(Optional.of(orderReturn));

            adminOrderService.updateOrderStatus(1L, request);

            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURN_COMPLETED);
            verify(memberClient).earnPoint(any(PointEarnRequest.class));
        }

        private void setUpOrder(DeliveryStatus status) {
            order = Order.builder().id(1L).userId(100L).deliveryStatus(status).build();
            Delivery delivery = Delivery.builder().order(order).build();
            ReflectionTestUtils.setField(order, "delivery", delivery);
        }

        private void setUpRequest(String status, String trackingNumber) {
            request = new OrderStatusUpdateRequest();
            ReflectionTestUtils.setField(request, "status", status);
            ReflectionTestUtils.setField(request, "trackingNumber", trackingNumber);
        }
    }

    @Nested
    @DisplayName("3. 반품 처리 (processReturn)")
    class ProcessReturnTest {

        private Order order;
        private OrderReturn orderReturn;

        @Test
        @DisplayName("반품 승인 - 전체 프로세스 성공 (환불금 적립 + 사용복구 + 적립회수)")
        void processReturn_Approve_Success() {
            // given
            setUpReturn(DeliveryStatus.DELIVERY_COMPLETED, 1000, 500); // 1000원 사용, 500원 적립됨
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            // when
            adminOrderService.processReturn(1L, true);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURN_COMPLETED);

            // 1. 환불금 포인트 적립
            verify(memberClient).earnPoint(any(PointEarnRequest.class));
            // 2. 사용했던 포인트 복구
            verify(memberClient).revertPoint(any(PointTransactionRequest.class));
            // 3. 적립되었던 포인트 회수 (MemberClient 명세에 따라 파라미터 2개 확인)
            verify(memberClient).deductPoint(eq(100L), eq(500));
        }

        @Test
        @DisplayName("반품 거절 - 배송 완료 상태로 원복")
        void processReturn_Reject() {
            // given
            setUpReturn(DeliveryStatus.DELIVERY_COMPLETED, 0, 0);
            given(orderReturnRepository.findByIdWithOrder(1L)).willReturn(Optional.of(orderReturn));

            // when
            adminOrderService.processReturn(1L, false);

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);
            verify(memberClient, times(0)).earnPoint(any());
        }

        private void setUpReturn(DeliveryStatus status, int usedPoint, int earnedPoint) {
            order = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .pointDiscount(usedPoint)
                    .earnedPoint(earnedPoint)
                    .deliveryStatus(status)
                    .build();

            orderReturn = OrderReturn.builder()
                    .id(1L)
                    .order(order)
                    .refundAmount(5000)
                    .returnReason(ReturnReason.PRODUCT_DEFECT)
                    .build();
            ReflectionTestUtils.setField(orderReturn, "order", order);
        }
    }

    @Nested
    @DisplayName("4. 자동 배송 완료 스케줄러")
    class SchedulerTest {
        @Test
        @DisplayName("3일 지난 배송 중 주문을 배송 완료로 변경")
        void completeOldDeliveries_Success() {
            // given
            Order order = Order.builder().id(1L).deliveryStatus(DeliveryStatus.DELIVERING).build();
            given(orderRepository.findAllByDeliveryStatusAndOrderDateBefore(eq(DeliveryStatus.DELIVERING), any(LocalDateTime.class)))
                    .willReturn(List.of(order));

            // when
            adminOrderService.completeOldDeliveries();

            // then
            assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_COMPLETED);
        }
    }
}